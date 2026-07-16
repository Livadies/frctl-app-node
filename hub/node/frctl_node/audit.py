from __future__ import annotations

import hashlib
import json
import os
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ZERO_HASH = "0" * 64


class AuditLog:
    def __init__(self, path: Path, limit: int = 5000):
        self.path = path
        self.limit = limit
        self.lock = threading.Lock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.touch()
            try:
                os.chmod(self.path, 0o600)
            except OSError:
                pass

    @staticmethod
    def _digest(payload: dict[str, Any]) -> str:
        return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()

    def _records_unlocked(self) -> list[dict[str, Any]]:
        records = []
        for line in self.path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                records.append(json.loads(line))
        return records

    def verify(self) -> bool:
        with self.lock:
            previous = ZERO_HASH
            for record in self._records_unlocked():
                digest = record.get("hash")
                body = {key: value for key, value in record.items() if key != "hash"}
                if body.get("previous") != previous or digest != self._digest(body):
                    return False
                previous = digest
            return True

    def append(self, *, connector: str, action: str, target: str, result: str, command: list[str], evidence: str | None = None) -> dict[str, Any]:
        with self.lock:
            records = self._records_unlocked()
            previous = records[-1]["hash"] if records else ZERO_HASH
            body = {
                "at": datetime.now(UTC).isoformat(timespec="seconds"),
                "connector": connector,
                "action": action,
                "target_hash": hashlib.sha256(target.encode("utf-8")).hexdigest(),
                "result": result,
                "command_hash": hashlib.sha256("\0".join(command).encode("utf-8")).hexdigest(),
                "previous": previous,
            }
            if evidence is not None:
                body["evidence_hash"] = hashlib.sha256(evidence.encode("utf-8")).hexdigest()
            record = {**body, "hash": self._digest(body)}
            records.append(record)
            if len(records) > self.limit:
                records = records[-self.limit :]
                records[0]["previous"] = ZERO_HASH
                rebuilt = []
                prior = ZERO_HASH
                for old in records:
                    rebuilt_body = {key: value for key, value in old.items() if key not in {"hash", "previous"}}
                    rebuilt_body["previous"] = prior
                    rebuilt_record = {**rebuilt_body, "hash": self._digest(rebuilt_body)}
                    rebuilt.append(rebuilt_record)
                    prior = rebuilt_record["hash"]
                records = rebuilt
            self.path.write_text("".join(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n" for item in records), encoding="utf-8")
            return record

    def recent(self, count: int = 20) -> list[dict[str, Any]]:
        with self.lock:
            records = self._records_unlocked()[-max(1, min(count, 100)) :]
        return [{key: value for key, value in record.items() if key not in {"command_hash", "previous"}} for record in records]
