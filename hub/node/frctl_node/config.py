from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, field
from pathlib import Path


def default_data_dir() -> Path:
    root = os.environ.get("LOCALAPPDATA") or str(Path.home() / ".local" / "share")
    return Path(root) / "FRCTL"


@dataclass
class NodeConfig:
    host: str = "127.0.0.1"
    port: int = 7878
    allowed_targets: list[str] = field(
        default_factory=lambda: ["localhost", "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "*.local", "*.example", "*.test"]
    )
    allowed_docker_images: list[str] = field(default_factory=lambda: ["alpine:3.20", "ubuntu:24.04"])
    allowed_identity_files: list[str] = field(default_factory=list)
    allowed_workflows: list[str] = field(default_factory=lambda: ["diagnostics", "proof-file"])
    ssh_host_keys: dict[str, str] = field(default_factory=dict)
    ssh_users: dict[str, str] = field(default_factory=dict)
    plink_paths: list[str] = field(
        default_factory=lambda: [
            r"%LOCALAPPDATA%\FRCTL\tools\plink.exe",
            r"C:\Program Files\PuTTY\plink.exe",
            r"C:\Program Files (x86)\PuTTY\plink.exe",
            r"%USERPROFILE%\Desktop\plink.exe",
            r"%USERPROFILE%\OneDrive\Desktop\plink.exe",
        ]
    )
    workflow_timeout_seconds: int = 45
    workflow_output_limit: int = 65536
    rustdesk_paths: list[str] = field(
        default_factory=lambda: [
            r"C:\Program Files\RustDesk\rustdesk.exe",
            r"C:\Program Files (x86)\RustDesk\rustdesk.exe",
        ]
    )
    putty_paths: list[str] = field(
        default_factory=lambda: [
            r"C:\Program Files\PuTTY\putty.exe",
            r"C:\Program Files (x86)\PuTTY\putty.exe",
            r"%USERPROFILE%\Desktop\putty.exe",
            r"%USERPROFILE%\OneDrive\Desktop\putty.exe",
        ]
    )
    open_browser: bool = True
    audit_limit: int = 5000

    def validate(self) -> None:
        if self.host != "127.0.0.1":
            raise ValueError("FRCTL Node may listen only on 127.0.0.1")
        if not 1024 <= self.port <= 65535:
            raise ValueError("Port must be between 1024 and 65535")
        if not isinstance(self.allowed_targets, list) or not all(isinstance(item, str) and item.strip() for item in self.allowed_targets):
            raise ValueError("At least one allowed target rule is required")
        if not isinstance(self.allowed_docker_images, list) or not all(isinstance(item, str) and item.strip() for item in self.allowed_docker_images):
            raise ValueError("At least one Docker image profile is required")
        if not isinstance(self.allowed_identity_files, list) or not all(isinstance(item, str) and item.strip() for item in self.allowed_identity_files):
            raise ValueError("allowed_identity_files must be a list of non-empty paths")
        if not isinstance(self.allowed_workflows, list) or not all(isinstance(item, str) and item.strip() for item in self.allowed_workflows):
            raise ValueError("allowed_workflows must be a list of non-empty ids")
        if not isinstance(self.ssh_host_keys, dict) or not all(isinstance(key, str) and isinstance(value, str) for key, value in self.ssh_host_keys.items()):
            raise ValueError("ssh_host_keys must be an object of string fingerprints")
        if not isinstance(self.ssh_users, dict) or not all(isinstance(key, str) and isinstance(value, str) for key, value in self.ssh_users.items()):
            raise ValueError("ssh_users must be an object of pinned usernames")
        if not isinstance(self.plink_paths, list) or not all(isinstance(item, str) for item in self.plink_paths):
            raise ValueError("plink_paths must be a list of strings")
        if not isinstance(self.workflow_timeout_seconds, int) or not 5 <= self.workflow_timeout_seconds <= 300:
            raise ValueError("workflow_timeout_seconds must be between 5 and 300")
        if not isinstance(self.workflow_output_limit, int) or not 4096 <= self.workflow_output_limit <= 1_048_576:
            raise ValueError("workflow_output_limit must be between 4096 and 1048576")
        if not isinstance(self.rustdesk_paths, list) or not all(isinstance(item, str) for item in self.rustdesk_paths):
            raise ValueError("rustdesk_paths must be a list of strings")
        if not isinstance(self.putty_paths, list) or not all(isinstance(item, str) for item in self.putty_paths):
            raise ValueError("putty_paths must be a list of strings")
        if not isinstance(self.audit_limit, int) or not 100 <= self.audit_limit <= 100_000:
            raise ValueError("audit_limit must be between 100 and 100000")

    @classmethod
    def load(cls, path: Path | None = None) -> tuple["NodeConfig", Path]:
        target = path or default_data_dir() / "node-config.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            raw = json.loads(target.read_text(encoding="utf-8"))
            allowed = {field.name for field in cls.__dataclass_fields__.values()}
            config = cls(**{key: value for key, value in raw.items() if key in allowed})
        else:
            config = cls()
            target.write_text(json.dumps(asdict(config), ensure_ascii=False, indent=2), encoding="utf-8")
            try:
                os.chmod(target, 0o600)
            except OSError:
                pass
        config.validate()
        return config, target
