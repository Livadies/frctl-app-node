import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from frctl_hub.server import create_server


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = create_server("127.0.0.1", 0)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def get_json(self, path):
        with urlopen(self.base + path) as response:
            return response.status, json.load(response), response.headers

    def post_json(self, path, payload):
        request = Request(self.base + path, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"}, method="POST")
        with urlopen(request) as response:
            return response.status, json.load(response)

    def test_health_and_security_headers(self):
        status, body, headers = self.get_json("/api/health")
        self.assertEqual(status, 200)
        self.assertFalse(body["execution"])
        self.assertIn("frame-ancestors 'none'", headers["Content-Security-Policy"])

    def test_plan_endpoint(self):
        status, body = self.post_json("/api/plan", {"connector": "browser", "target": "portal.example", "action": "open-workspace", "dry_run": True})
        self.assertEqual(status, 201)
        self.assertEqual(body["mode"], "dry-run")

    def test_unknown_route_is_404(self):
        with self.assertRaises(HTTPError) as caught:
            urlopen(self.base + "/api/missing")
        self.assertEqual(caught.exception.code, 404)


if __name__ == "__main__":
    unittest.main()

