import http.cookiejar
import json
import socket
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener

from frctl_node.audit import AuditLog
from frctl_node.config import NodeConfig
from frctl_node.server import NodeService, create_server


class AllowConfirmer:
    available = True

    def confirm(self, title, message):
        return True


class UnavailableConfirmer:
    available = False
    failure_reason = "TclError"

    def confirm(self, title, message):
        return False


class FakeLauncher:
    def __init__(self):
        self.plans = []

    def launch(self, plan):
        self.plans.append(plan)
        return 4242


class FakeWorkflowExecutor:
    def __init__(self):
        self.plans = []

    def run(self, plan):
        self.plans.append(plan)
        return {"output": "root\ne2k\n", "returncode": 0, "duration_ms": 17}


class FakeMarketplace:
    def snapshot(self):
        return {"schema": 1, "cached": False, "entries": [{"id": "hf:org/model", "kind": "ai-model"}]}


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.port = free_port()
        root = Path(cls.temp.name)
        cls.key = root / "identity.ppk"
        cls.plink = root / "plink.exe"
        cls.key.write_text("test", encoding="utf-8")
        cls.plink.write_text("test", encoding="utf-8")
        cls.config = NodeConfig(
            port=cls.port,
            allowed_identity_files=[str(cls.key)],
            plink_paths=[str(cls.plink)],
            ssh_host_keys={"server.example:22": "ssh-rsa 2048 SHA256:aqhd4baMSoim0g9cp/N5ijj7OiirHKYKwJgeREDboAw"},
        )
        cls.launcher = FakeLauncher()
        cls.workflow_executor = FakeWorkflowExecutor()
        audit = AuditLog(Path(cls.temp.name) / "audit.jsonl")
        cls.server, cls.service = create_server(
            cls.config,
            confirmer=AllowConfirmer(),
            launcher=cls.launcher,
            workflow_executor=cls.workflow_executor,
            audit=audit,
            marketplace=FakeMarketplace(),
        )
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.port}"
        cls.opener = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
        with cls.opener.open(cls.base + "/") as response:
            response.read()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.temp.cleanup()

    def request(self, path, payload=None, origin=None):
        headers = {}
        data = None
        if payload is not None:
            data = json.dumps(payload).encode()
            headers.update({"Content-Type": "application/json", "X-FRCTL-Request": "1", "Origin": origin or self.base})
        request = Request(self.base + path, data=data, headers=headers, method="POST" if payload is not None else "GET")
        with self.opener.open(request) as response:
            return response.status, json.load(response), response.headers

    def test_status_is_local_and_audit_verified(self):
        status, body, headers = self.request("/api/status")
        self.assertEqual(status, 200)
        self.assertTrue(body["loopback_only"])
        self.assertTrue(body["audit_verified"])
        self.assertIn("frame-ancestors 'none'", headers["Content-Security-Policy"])

    def test_status_reports_fail_closed_confirmation(self):
        service = NodeService(
            self.config,
            confirmer=UnavailableConfirmer(),
            launcher=self.launcher,
            workflow_executor=self.workflow_executor,
            audit=AuditLog(Path(self.temp.name) / "unavailable-audit.jsonl"),
            marketplace=FakeMarketplace(),
        )
        status = service.status()
        self.assertFalse(status["native_confirmation"])
        self.assertTrue(status["confirmation_fail_closed"])
        self.assertEqual("TclError", status["confirmation_failure"])

    def test_marketplace_is_same_origin_and_normalized(self):
        status, body, _ = self.request("/api/marketplace")
        self.assertEqual(status, 200)
        self.assertEqual("ai-model", body["entries"][0]["kind"])

    def test_audit_can_be_verified_exported_and_streamed(self):
        status, body, _ = self.request("/api/audit")
        self.assertEqual(200, status)
        self.assertTrue(body["verified"])
        with self.opener.open(self.base + "/api/audit/export") as response:
            self.assertEqual("application/x-ndjson; charset=utf-8", response.headers["Content-Type"])
            self.assertIn("attachment", response.headers["Content-Disposition"])
        with self.opener.open(self.base + "/api/audit/stream", timeout=3) as response:
            self.assertEqual("text/event-stream; charset=utf-8", response.headers["Content-Type"])
            self.assertEqual(b"event: audit\n", response.readline())

    def test_audit_stream_limit_returns_429(self):
        acquired = 0
        while self.service.try_open_audit_stream():
            acquired += 1
        try:
            with self.assertRaises(HTTPError) as caught:
                self.request("/api/audit/stream")
            self.assertEqual(429, caught.exception.code)
        finally:
            for _ in range(acquired):
                self.service.close_audit_stream()

    def test_plan_and_launch(self):
        payload = {"connector": "ssh", "target": "server.example", "client": "openssh", "user": "ubuntu"}
        status, plan, _ = self.request("/api/plan", payload)
        self.assertEqual(status, 200)
        self.assertTrue(plan["requires_native_confirmation"])
        if plan["installed"]:
            status, result, _ = self.request("/api/launch", payload)
            self.assertEqual(status, 200)
            self.assertTrue(result["launched"])
            self.assertEqual(result["pid"], 4242)

    def test_cross_origin_post_is_rejected(self):
        with self.assertRaises(HTTPError) as caught:
            self.request("/api/plan", {"connector": "ssh", "target": "server.example"}, origin="https://attacker.example")
        self.assertEqual(caught.exception.code, 401)

    def test_arbitrary_command_is_rejected(self):
        with self.assertRaises(HTTPError) as caught:
            self.request("/api/plan", {"connector": "ssh", "target": "server.example", "command": "whoami"})
        self.assertEqual(caught.exception.code, 422)

    def test_workflow_plan_and_run(self):
        payload = {
            "target": "server.example",
            "user": "operator",
            "identity_file": str(self.key),
            "workflow": "diagnostics",
        }
        status, plan, _ = self.request("/api/workflow/plan", payload)
        self.assertEqual(status, 200)
        self.assertEqual(plan["host_key"], self.config.ssh_host_keys["server.example:22"])
        status, result, _ = self.request("/api/workflow/run", payload)
        self.assertEqual(status, 200)
        self.assertTrue(result["executed"])
        self.assertIn("e2k", result["output"])
        self.assertTrue(result["receipt"])


if __name__ == "__main__":
    unittest.main()
