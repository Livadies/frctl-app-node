import tempfile
import unittest
import hashlib
import json
from pathlib import Path

from frctl_node.audit import AuditLog
from frctl_node.config import NodeConfig
from frctl_node.connectors import ConnectorRegistry
from frctl_node.policy import PolicyError, parse_target, target_allowed


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.config = NodeConfig()
        self.registry = ConnectorRegistry(self.config)

    def test_private_and_documentation_targets_are_allowed(self):
        self.assertTrue(target_allowed("10.2.3.4", self.config.allowed_targets))
        self.assertTrue(target_allowed("server.example", self.config.allowed_targets))
        self.assertFalse(target_allowed("example.com", self.config.allowed_targets))

    def test_credentials_and_query_are_rejected(self):
        with self.assertRaises(PolicyError):
            parse_target("root:password@server.example")
        with self.assertRaises(PolicyError):
            parse_target("server.example?token=secret")

    def test_arbitrary_commands_are_rejected(self):
        with self.assertRaises(PolicyError):
            self.registry.plan({"connector": "ssh", "target": "server.example", "command": "whoami"})

    def test_unknown_fields_are_rejected(self):
        with self.assertRaisesRegex(PolicyError, "неизвестные"):
            self.registry.plan({"connector": "ssh", "target": "localhost", "environment": {"A": "B"}})

    def test_invalid_hostname_and_port_are_rejected(self):
        for target in ("server..example", "localhost:0"):
            with self.subTest(target=target), self.assertRaises(PolicyError):
                self.registry.plan({"connector": "ssh", "target": target})

    def test_openssh_plan_never_contains_password(self):
        plan = self.registry.plan({"connector": "ssh", "target": "10.0.0.4:2222", "user": "ubuntu", "client": "openssh"})
        joined = " ".join(plan.command).lower()
        self.assertNotIn("password", joined)
        self.assertIn("stricthostkeychecking=ask", joined)
        self.assertIn("ubuntu@10.0.0.4", plan.command)

    def test_putty_uses_only_allowlisted_ppk_and_masks_path(self):
        with tempfile.TemporaryDirectory() as temp:
            key = Path(temp) / "test identity.ppk"
            key.write_text("test", encoding="utf-8")
            config = NodeConfig(allowed_identity_files=[str(key)])
            plan = ConnectorRegistry(config).plan(
                {"connector": "ssh", "target": "localhost", "client": "putty", "identity_file": str(key)}
            )
            self.assertEqual(plan.command[-2:], ["-i", str(key.resolve())])
            self.assertNotIn(str(key.resolve()), plan.public()["command_preview"])
            self.assertIn(key.name, plan.public()["command_preview"])

    def test_unlisted_ppk_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            key = Path(temp) / "unknown.ppk"
            key.write_text("test", encoding="utf-8")
            with self.assertRaisesRegex(PolicyError, "allowlist"):
                self.registry.plan({"connector": "ssh", "target": "localhost", "client": "putty", "identity_file": str(key)})

    def test_rustdesk_plan_connects_without_password(self):
        plan = self.registry.plan({"connector": "rustdesk", "target": "123456789"})
        self.assertEqual(plan.command[-2:], ["--connect", "123456789"])
        self.assertNotIn("--password", plan.command)

    def test_docker_profile_is_hardened(self):
        plan = self.registry.plan({"connector": "docker", "action": "open-workspace", "image": "alpine:3.20"})
        joined = " ".join(plan.command)
        for flag in ("--read-only", "--cap-drop=ALL", "--network=none", "--security-opt=no-new-privileges"):
            self.assertIn(flag, joined)
        self.assertNotIn("--privileged", joined)
        self.assertNotIn("--volume", joined)

    def test_unlisted_docker_image_is_rejected(self):
        with self.assertRaises(PolicyError):
            self.registry.plan({"connector": "docker", "action": "open-workspace", "image": "unknown:latest"})

    def test_docker_image_cannot_be_an_option(self):
        config = NodeConfig(allowed_docker_images=["--privileged"])
        with self.assertRaisesRegex(PolicyError, "Docker image"):
            ConnectorRegistry(config).plan({"connector": "docker", "image": "--privileged", "action": "open-workspace"})

    def test_audit_chain_detects_tampering(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "audit.jsonl"
            audit = AuditLog(path)
            audit.append(connector="ssh", action="connect", target="server.example", result="denied", command=["ssh", "server.example"])
            audit.append(connector="docker", action="open-workspace", target="alpine:3.20", result="launched", command=["docker", "run"])
            self.assertTrue(audit.verify())
            self.assertEqual(32, audit.key_path.stat().st_size)
            self.assertNotIn(audit.key_path.read_bytes().hex(), path.read_text(encoding="utf-8"))
            path.write_text(path.read_text(encoding="utf-8").replace('"denied"', '"launched"', 1), encoding="utf-8")
            self.assertFalse(audit.verify())

    def test_plain_sha256_rewrite_cannot_forge_hmac_audit(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "audit.jsonl"
            audit = AuditLog(path)
            audit.append(connector="ssh", action="connect", target="server.example", result="denied", command=["ssh"])
            record = json.loads(path.read_text(encoding="utf-8"))
            record["result"] = "launched"
            body = {key: value for key, value in record.items() if key != "hash"}
            canonical = json.dumps(body, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
            record["hash"] = hashlib.sha256(canonical).hexdigest()
            path.write_text(json.dumps(record) + "\n", encoding="utf-8")
            self.assertFalse(audit.verify())


if __name__ == "__main__":
    unittest.main()
