import unittest

from frctl_hub.core import RuntimeState, ValidationError, build_plan, normalize_target, redact_event


class PolicyTests(unittest.TestCase):
    def test_plan_is_non_executable(self):
        plan = build_plan({"connector": "ssh", "target": "10.0.0.8:22", "action": "inspect", "dry_run": True})
        self.assertFalse(plan["executable"])
        self.assertEqual(plan["target"]["scope"], "private")

    def test_execution_request_is_rejected(self):
        with self.assertRaises(ValidationError):
            build_plan({"connector": "ssh", "target": "server.example", "action": "connect", "dry_run": False})

    def test_credentials_in_target_are_rejected(self):
        with self.assertRaises(ValidationError):
            normalize_target("ssh://root:password@server.example")

    def test_telemetry_requires_consent_and_redacts(self):
        with self.assertRaises(ValidationError):
            redact_event({"name": "catalog_open", "consent": False})
        event = redact_event({"name": "plan_created", "consent": True, "properties": {"token": "abc", "scope": "private"}})
        self.assertEqual(event["properties"]["token"], "[redacted]")
        self.assertEqual(event["properties"]["scope"], "private")
        event = redact_event({"name": "plan_created", "consent": True, "properties": {"target": "sensitive.example"}})
        self.assertNotIn("target", event["properties"])

    def test_receipts_form_hash_chain(self):
        state = RuntimeState()
        first = state.append_receipt({"kind": "plan", "subject": "demo-1"})
        second = state.append_receipt({"kind": "plan", "subject": "demo-2"})
        self.assertEqual(second["previous"], first["hash"])
        self.assertEqual(len(second["hash"]), 64)
        self.assertNotIn("subject", second)
        self.assertEqual(len(second["subject_hash"]), 64)


if __name__ == "__main__":
    unittest.main()
