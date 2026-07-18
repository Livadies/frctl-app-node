from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ZERO_HASH = "0" * 64
AUDIT_VERSION = 2


class AuditLog:
    def __init__(self, path: Path, limit: int = 5000, key_path: Path | None = None):
        self.path = path
        self.key_path = key_path or path.with_name("audit.key")
        self.limit = limit
        self.lock = threading.Lock()
        self.changed = threading.Condition(self.lock)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.touch()
            self._restrict(self.path)
        self.key, created = self._read_or_create_key()
        if created and self.path.stat().st_size:
            with self.lock:
                records = self._records_unlocked()
                if self._verify_records(records, legacy=True):
                    self._write_records_unlocked(self._resign(records))

    @staticmethod
    def _restrict(path: Path) -> None:
        try:
            os.chmod(path, 0o600)
        except OSError:
            pass

    def _read_or_create_key(self) -> tuple[bytes, bool]:
        created = False
        try:
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
            descriptor = os.open(self.key_path, flags, 0o600)
        except FileExistsError:
            descriptor = None
        if descriptor is not None:
            created = True
            key = secrets.token_bytes(32)
            try:
                written = 0
                while written < len(key):
                    count = os.write(descriptor, key[written:])
                    if count <= 0:
                        raise OSError("Не удалось полностью записать audit.key")
                    written += count
                os.fsync(descriptor)
            except Exception:
                self.key_path.unlink(missing_ok=True)
                raise
            finally:
                os.close(descriptor)
            self._restrict(self.key_path)
        key = self.key_path.read_bytes()
        if len(key) != 32:
            raise RuntimeError("audit.key повреждён: ожидалось 32 байта")
        return key, created

    @staticmethod
    def _canonical(payload: dict[str, Any]) -> bytes:
        return json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    def _digest(self, payload: dict[str, Any]) -> str:
        return hmac.new(self.key, self._canonical(payload), hashlib.sha256).hexdigest()

    @classmethod
    def _legacy_digest(cls, payload: dict[str, Any]) -> str:
        return hashlib.sha256(cls._canonical(payload)).hexdigest()

    def _records_unlocked(self) -> list[dict[str, Any]]:
        records = []
        for line in self.path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                records.append(json.loads(line))
        return records

    def _verify_records(self, records: list[dict[str, Any]], *, legacy: bool = False) -> bool:
        previous = ZERO_HASH
        for record in records:
            digest = record.get("hash")
            body = {key: value for key, value in record.items() if key != "hash"}
            expected = self._legacy_digest(body) if legacy else self._digest(body)
            if body.get("previous") != previous or digest is None or not hmac.compare_digest(str(digest), expected):
                return False
            if not legacy and body.get("version") != AUDIT_VERSION:
                return False
            previous = str(digest)
        return True

    def _resign(self, records: list[dict[str, Any]]) -> list[dict[str, Any]]:
        rebuilt = []
        previous = ZERO_HASH
        for old in records:
            body = {key: value for key, value in old.items() if key not in {"hash", "previous", "version"}}
            body.update({"version": AUDIT_VERSION, "previous": previous})
            record = {**body, "hash": self._digest(body)}
            rebuilt.append(record)
            previous = record["hash"]
        return rebuilt

    def _write_records_unlocked(self, records: list[dict[str, Any]]) -> None:
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        temporary.write_text("".join(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n" for item in records), encoding="utf-8")
        self._restrict(temporary)
        os.replace(temporary, self.path)
        self._restrict(self.path)

    def verify(self) -> bool:
        with self.lock:
            try:
                return self._verify_records(self._records_unlocked())
            except (OSError, ValueError, TypeError, json.JSONDecodeError):
                return False

    def append(self, *, connector: str, action: str, target: str, result: str, command: list[str], evidence: str | None = None) -> dict[str, Any]:
        with self.changed:
            records = self._records_unlocked()
            if records and not self._verify_records(records):
                raise RuntimeError("Целостность журнала аудита нарушена; новая запись отклонена")
            previous = records[-1]["hash"] if records else ZERO_HASH
            body = {
                "version": AUDIT_VERSION,
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
                records = self._resign(records[-self.limit :])
                record = records[-1]
            self._write_records_unlocked(records)
            self.changed.notify_all()
            return record

    def recent(self, count: int = 20) -> list[dict[str, Any]]:
        with self.lock:
            records = self._records_unlocked()[-max(1, min(count, 100)) :]
        return self._public(records)

    @staticmethod
    def _public(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [{key: value for key, value in record.items() if key not in {"command_hash", "previous"}} for record in records]

    def wait_for_change(self, after_hash: str, timeout: float = 15.0) -> list[dict[str, Any]]:
        with self.changed:
            def has_changed() -> bool:
                records = self._records_unlocked()
                latest = str(records[-1].get("hash", "")) if records else ""
                return latest != after_hash

            self.changed.wait_for(has_changed, timeout=timeout)
            return self._public(self._records_unlocked()[-20:])

    def export_bytes(self) -> bytes:
        with self.lock:
            if not self._verify_records(self._records_unlocked()):
                raise RuntimeError("Целостность журнала аудита нарушена")
            return self.path.read_bytes()
