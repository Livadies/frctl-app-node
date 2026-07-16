import tempfile
import unittest
from pathlib import Path

from frctl_node.config import NodeConfig
from frctl_node.policy import PolicyError
from frctl_node.workflows import BUILTIN_WORKFLOWS, WorkflowRegistry, _clean_output, _extract_workflow_output


FINGERPRINT = "ssh-rsa 2048 SHA256:aqhd4baMSoim0g9cp/N5ijj7OiirHKYKwJgeREDboAw"


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.key = root / "identity.ppk"
        self.plink = root / "plink.exe"
        self.key.write_text("test", encoding="utf-8")
        self.plink.write_text("test", encoding="utf-8")
        self.config = NodeConfig(
            allowed_targets=["server.example"],
            allowed_identity_files=[str(self.key)],
            plink_paths=[str(self.plink)],
            ssh_host_keys={"server.example:8194": FINGERPRINT},
            ssh_users={"server.example:8194": "operator"},
        )
        self.registry = WorkflowRegistry(self.config)

    def tearDown(self):
        self.temp.cleanup()

    def payload(self):
        return {
            "target": "server.example:8194",
            "user": "operator",
            "identity_file": str(self.key),
            "workflow": "diagnostics",
        }

    def test_plan_pins_host_key_and_contains_only_builtin_commands(self):
        plan = self.registry.plan(self.payload())
        self.assertEqual(plan.host_key, FINGERPRINT)
        self.assertIn("-hostkey", plan.command)
        self.assertIn("whoami", plan.definition.commands)
        self.assertNotIn(str(self.key), plan.public().values())
        self.assertFalse(plan.public()["arbitrary_commands"])

    def test_arbitrary_command_and_unknown_fields_are_rejected(self):
        payload = self.payload()
        payload["command"] = "rm -rf /"
        with self.assertRaisesRegex(PolicyError, "произвольные"):
            self.registry.plan(payload)

    def test_missing_host_key_pin_is_rejected(self):
        self.config.ssh_host_keys = {}
        with self.assertRaisesRegex(PolicyError, "host-key"):
            self.registry.plan(self.payload())

    def test_unlisted_identity_is_rejected(self):
        other = Path(self.temp.name) / "other.ppk"
        other.write_text("test", encoding="utf-8")
        payload = self.payload()
        payload["identity_file"] = str(other)
        with self.assertRaisesRegex(PolicyError, "allowlist"):
            self.registry.plan(payload)

    def test_user_must_match_pinned_target_profile(self):
        payload = self.payload()
        payload["user"] = "root"
        with self.assertRaisesRegex(PolicyError, "закреплённым профилем"):
            self.registry.plan(payload)

    def test_output_is_sanitized_and_bounded(self):
        output = _clean_output("\x1b[31mhello\x1b[0m\x00" + "x" * 5000, 4096)
        self.assertNotIn("\x1b", output)
        self.assertNotIn("\x00", output)
        self.assertIn("OUTPUT TRUNCATED", output)

    def test_terminal_preamble_is_removed(self):
        output = "echo ===== FRCTL_DIAGNOSTICS =====\nroot# echo ===== FRCTL_DIAGNOSTICS =====\n===== FRCTL_DIAGNOSTICS =====\nroot"
        extracted = _extract_workflow_output(output, BUILTIN_WORKFLOWS["diagnostics"])
        self.assertTrue(extracted.startswith("===== FRCTL_DIAGNOSTICS ====="))
        self.assertEqual(extracted.count("FRCTL_DIAGNOSTICS"), 1)


if __name__ == "__main__":
    unittest.main()
