from __future__ import annotations

import hashlib
import ipaddress
import json
import re
import threading
from collections import Counter, deque
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from urllib.parse import urlparse


CONNECTORS = (
    {
        "id": "ssh",
        "name": "SSH / PuTTY",
        "kind": "remote-shell",
        "runtime": "external-client",
        "risk": "medium",
        "description": "Plan an SSH session and hand it to an installed OSS client.",
    },
    {
        "id": "rustdesk",
        "name": "RustDesk",
        "kind": "remote-desktop",
        "runtime": "external-client",
        "risk": "high",
        "description": "Plan a consent-based remote desktop session without embedding credentials.",
    },
    {
        "id": "browser",
        "name": "Browser workspace",
        "kind": "web",
        "runtime": "chromium-or-gecko",
        "risk": "low",
        "description": "Open an isolated web workspace in Chromium or Gecko.",
    },
    {
        "id": "sandbox",
        "name": "Ephemeral sandbox",
        "kind": "workspace",
        "runtime": "docker-or-kubernetes",
        "risk": "medium",
        "description": "Describe an isolated disposable workspace with deny-by-default policy.",
    },
)

ALLOWED_ACTIONS = {"inspect", "connect", "open-workspace", "collect-diagnostics"}
SENSITIVE_KEYS = {"token", "password", "secret", "authorization", "cookie", "session", "key"}
SAFE_EVENT_NAMES = {"catalog_open", "connector_select", "plan_created", "suggestion_used", "consent_change"}
SAFE_PROPERTY_VALUES = {
    "connector": {item["id"] for item in CONNECTORS},
    "scope": {"private", "public", "hostname", "documentation"},
}
SAFE_BOOLEAN_PROPERTIES = {"enabled"}
SAFE_INTEGER_PROPERTIES = {"count"}
HOST_RE = re.compile(r"^[a-zA-Z0-9](?:[a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?(?::\d{1,5})?$")


class ValidationError(ValueError):
    pass


def utc_now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds")


def normalize_target(raw: str) -> dict[str, Any]:
    value = raw.strip()
    if not value or len(value) > 255:
        raise ValidationError("Target must contain 1-255 characters")
    if any(ch in value for ch in "\r\n\t @\\"):
        raise ValidationError("Target contains forbidden characters")

    parsed = urlparse(value if "://" in value else f"ssh://{value}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValidationError("Credentials, query strings and fragments are not accepted")
    if parsed.scheme not in {"ssh", "https", "http", "rustdesk"}:
        raise ValidationError("Unsupported target scheme")
    host = parsed.hostname or ""
    if not host or not HOST_RE.fullmatch(parsed.netloc or host):
        raise ValidationError("Target must be a hostname or IP address")
    if parsed.port is not None and not 1 <= parsed.port <= 65535:
        raise ValidationError("Target port is outside 1-65535")

    scope = "hostname"
    try:
        ip = ipaddress.ip_address(host)
        scope = "private" if ip.is_private or ip.is_loopback else "public"
    except ValueError:
        if host == "localhost" or host.endswith(".local"):
            scope = "private"
        elif host.endswith(".example") or host.endswith(".test"):
            scope = "documentation"

    return {
        "scheme": parsed.scheme,
        "host": host,
        "port": parsed.port,
        "scope": scope,
        "display": f"{host}:{parsed.port}" if parsed.port else host,
    }


def build_plan(payload: dict[str, Any]) -> dict[str, Any]:
    connector_id = str(payload.get("connector", ""))
    action = str(payload.get("action", "inspect"))
    if payload.get("dry_run") is not True:
        raise ValidationError("The public control plane only accepts dry_run=true")
    connector = next((item for item in CONNECTORS if item["id"] == connector_id), None)
    if connector is None:
        raise ValidationError("Unknown connector")
    if action not in ALLOWED_ACTIONS:
        raise ValidationError("Unsupported action")
    target = normalize_target(str(payload.get("target", "")))

    controls = [
        "Confirm the operator owns or is authorized to access the target",
        "Create an ephemeral workspace with no host filesystem mounts",
        "Keep credentials in the local OS credential vault",
        "Require an explicit user confirmation before any network session",
        "Emit a redacted audit receipt and destroy the workspace on exit",
    ]
    if target["scope"] == "public":
        controls.insert(1, "Apply the organization allowlist before contacting a public target")
    return {
        "mode": "dry-run",
        "executable": False,
        "created_at": utc_now(),
        "connector": connector,
        "action": action,
        "target": target,
        "controls": controls,
        "next_boundary": "Execution requires a separately installed, authenticated local node.",
    }


def redact_event(payload: dict[str, Any]) -> dict[str, Any]:
    if payload.get("consent") is not True:
        raise ValidationError("Telemetry consent is required")
    name = str(payload.get("name", ""))
    if name not in SAFE_EVENT_NAMES:
        raise ValidationError("Event name is not allowlisted")
    properties = payload.get("properties", {})
    if not isinstance(properties, dict):
        raise ValidationError("Event properties must be an object")

    clean: dict[str, str | int | float | bool] = {}
    for key, value in list(properties.items())[:12]:
        normalized_key = str(key).lower()[:40]
        if any(marker in normalized_key for marker in SENSITIVE_KEYS):
            clean[normalized_key] = "[redacted]"
        elif normalized_key in SAFE_PROPERTY_VALUES and str(value) in SAFE_PROPERTY_VALUES[normalized_key]:
            clean[normalized_key] = str(value)
        elif normalized_key in SAFE_BOOLEAN_PROPERTIES and isinstance(value, bool):
            clean[normalized_key] = value
        elif normalized_key in SAFE_INTEGER_PROPERTIES and isinstance(value, int) and 0 <= value <= 10_000:
            clean[normalized_key] = value
    return {"name": name, "properties": clean, "received_at": utc_now()}


@dataclass
class RuntimeState:
    events: deque[dict[str, Any]] = field(default_factory=lambda: deque(maxlen=200))
    counts: Counter[str] = field(default_factory=Counter)
    receipts: deque[dict[str, Any]] = field(default_factory=lambda: deque(maxlen=200))
    lock: threading.Lock = field(default_factory=threading.Lock)

    def accept_event(self, payload: dict[str, Any]) -> dict[str, Any]:
        event = redact_event(payload)
        with self.lock:
            self.events.append(event)
            self.counts[event["name"]] += 1
        return {"accepted": True, "event": event, "stored": "ephemeral-memory"}

    def append_receipt(self, payload: dict[str, Any]) -> dict[str, Any]:
        kind = str(payload.get("kind", "workflow"))[:32]
        subject = str(payload.get("subject", "anonymous"))[:80]
        if not re.fullmatch(r"[a-zA-Z0-9_.:-]+", kind):
            raise ValidationError("Receipt kind contains forbidden characters")
        if any(marker in subject.lower() for marker in ("token", "password", "secret=")):
            raise ValidationError("Receipt subject appears to contain a secret")
        subject_hash = hashlib.sha256(subject.encode("utf-8")).hexdigest()
        with self.lock:
            previous = self.receipts[-1]["hash"] if self.receipts else "0" * 64
            body = {"kind": kind, "subject_hash": subject_hash, "created_at": utc_now(), "previous": previous}
            digest = hashlib.sha256(json.dumps(body, sort_keys=True).encode("utf-8")).hexdigest()
            receipt = {**body, "hash": digest}
            self.receipts.append(receipt)
        return receipt

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {
                "telemetry_counts": dict(self.counts),
                "receipt_count": len(self.receipts),
                "last_receipt_hash": self.receipts[-1]["hash"] if self.receipts else None,
                "persistence": "none",
            }
