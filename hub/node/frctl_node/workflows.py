from __future__ import annotations

import os
import re
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .config import NodeConfig
from .policy import PolicyError, parse_target, target_allowed, validate_user


ANSI_RE = re.compile(r"\x1b(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")
HOST_KEY_RE = re.compile(r"^[A-Za-z0-9@._+-]+\s+\d{2,5}\s+SHA256:[A-Za-z0-9+/=]{20,100}$")
WORKFLOW_FIELDS = {"target", "user", "identity_file", "workflow"}


@dataclass(frozen=True)
class WorkflowDefinition:
    id: str
    name: str
    description: str
    commands: tuple[str, ...]
    mutating: bool = False


BUILTIN_WORKFLOWS: dict[str, WorkflowDefinition] = {
    "diagnostics": WorkflowDefinition(
        id="diagnostics",
        name="Безопасная диагностика",
        description="Показывает пользователя, ОС, архитектуру, время, uptime и свободное место.",
        commands=(
            "echo ===== FRCTL_DIAGNOSTICS =====",
            "whoami",
            "hostname",
            "pwd",
            "uname -m",
            "head -n 3 /etc/os-release",
            "date -u",
            "uptime",
            "df -h /",
        ),
    ),
    "proof-file": WorkflowDefinition(
        id="proof-file",
        name="Контрольный файл",
        description="Создаёт /tmp/frctl-proof.txt с правами только владельца и читает его обратно.",
        commands=(
            "echo ===== FRCTL_PROOF_FILE =====",
            "umask 077",
            "echo FRCTL_NODE_REMOTE_PROOF > /tmp/frctl-proof.txt",
            "ls -l /tmp/frctl-proof.txt",
            "cat /tmp/frctl-proof.txt",
        ),
        mutating=True,
    ),
}


def _find_plink(config: NodeConfig) -> str | None:
    found = shutil.which("plink")
    if found:
        return found
    for item in config.plink_paths:
        path = Path(os.path.expandvars(item)).expanduser()
        if path.is_file():
            return str(path)
    return None


def _resolve_identity(config: NodeConfig, raw: Any) -> str:
    value = str(raw or "").strip()
    if not value:
        raise PolicyError("Для SSH workflow требуется разрешённый PPK identity")
    try:
        resolved = Path(os.path.expandvars(value)).expanduser().resolve(strict=True)
    except OSError as exc:
        raise PolicyError("Identity file не найден") from exc
    allowed = set()
    for item in config.allowed_identity_files:
        try:
            path = Path(os.path.expandvars(item)).expanduser().resolve(strict=True)
            allowed.add(os.path.normcase(str(path)))
        except OSError:
            continue
    if os.path.normcase(str(resolved)) not in allowed:
        raise PolicyError("Identity file отсутствует в allowlist")
    if resolved.suffix.lower() != ".ppk":
        raise PolicyError("SSH workflow принимает только разрешённый PPK identity")
    return str(resolved)


@dataclass(frozen=True)
class WorkflowPlan:
    definition: WorkflowDefinition
    target: str
    host: str
    port: int
    user: str
    identity_file: str
    host_key: str
    executable: str
    installed: bool

    @property
    def command(self) -> list[str]:
        return [
            self.executable,
            "-batch",
            "-noagent",
            "-no-trivial-auth",
            "-t",
            "-ssh",
            "-P",
            str(self.port),
            "-hostkey",
            self.host_key,
            "-i",
            self.identity_file,
            f"{self.user}@{self.host}",
        ]

    @property
    def stdin(self) -> str:
        return "\n".join((*self.definition.commands, "exit", ""))

    def public(self) -> dict[str, Any]:
        return {
            "workflow": self.definition.id,
            "name": self.definition.name,
            "description": self.definition.description,
            "target": self.target,
            "user": self.user,
            "identity": Path(self.identity_file).name,
            "host_key": self.host_key,
            "installed": self.installed,
            "commands": list(self.definition.commands),
            "mutating": self.definition.mutating,
            "requires_native_confirmation": True,
            "arbitrary_commands": False,
        }


class WorkflowRegistry:
    def __init__(self, config: NodeConfig):
        self.config = config

    def catalog(self) -> list[dict[str, Any]]:
        return [
            {
                "id": definition.id,
                "name": definition.name,
                "description": definition.description,
                "mutating": definition.mutating,
            }
            for workflow_id, definition in BUILTIN_WORKFLOWS.items()
            if workflow_id in self.config.allowed_workflows
        ]

    def plink_ready(self) -> bool:
        return bool(_find_plink(self.config))

    def plan(self, payload: dict[str, Any]) -> WorkflowPlan:
        if set(payload) - WORKFLOW_FIELDS:
            raise PolicyError("SSH workflow API содержит неизвестные или произвольные параметры")
        workflow_id = str(payload.get("workflow", ""))
        if workflow_id not in self.config.allowed_workflows or workflow_id not in BUILTIN_WORKFLOWS:
            raise PolicyError("Workflow отсутствует в локальном allowlist")
        target = parse_target(str(payload.get("target", "")), "ssh")
        if not target_allowed(target.host, self.config.allowed_targets):
            raise PolicyError("Target не входит в allowlist")
        user = validate_user(payload.get("user"))
        if not user:
            raise PolicyError("Для SSH workflow требуется имя пользователя")
        identity_file = _resolve_identity(self.config, payload.get("identity_file"))
        port = target.port or 22
        profile_key = f"{target.host}:{port}"
        pinned_user = str(self.config.ssh_users.get(profile_key, "")).strip()
        if pinned_user and user != pinned_user:
            raise PolicyError("SSH user не совпадает с закреплённым профилем target")
        host_key = str(self.config.ssh_host_keys.get(profile_key, "")).strip()
        if not HOST_KEY_RE.fullmatch(host_key):
            raise PolicyError("Для target отсутствует проверенный SSH host-key fingerprint")
        executable = _find_plink(self.config) or "plink.exe"
        return WorkflowPlan(
            definition=BUILTIN_WORKFLOWS[workflow_id],
            target=target.display,
            host=target.host,
            port=port,
            user=user,
            identity_file=identity_file,
            host_key=host_key,
            executable=executable,
            installed=bool(_find_plink(self.config)),
        )


class WorkflowExecutionError(RuntimeError):
    def __init__(self, message: str, *, output: str = "", returncode: int | None = None):
        super().__init__(message)
        self.output = output
        self.returncode = returncode


class WorkflowExecutor:
    def __init__(self, config: NodeConfig):
        self.config = config

    def run(self, plan: WorkflowPlan) -> dict[str, Any]:
        if not plan.installed:
            raise FileNotFoundError("Plink не установлен")
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        started = time.monotonic()
        try:
            completed = subprocess.run(
                plan.command,
                input=plan.stdin,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                encoding="utf-8",
                errors="replace",
                timeout=self.config.workflow_timeout_seconds,
                creationflags=flags,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            output = _clean_output(exc.stdout or "", self.config.workflow_output_limit)
            raise WorkflowExecutionError("SSH workflow превысил лимит времени", output=output) from exc
        output = _extract_workflow_output(
            _clean_output(completed.stdout, self.config.workflow_output_limit),
            plan.definition,
        )
        if completed.returncode != 0:
            raise WorkflowExecutionError(
                f"SSH workflow завершился с кодом {completed.returncode}",
                output=output,
                returncode=completed.returncode,
            )
        return {
            "output": output,
            "returncode": completed.returncode,
            "duration_ms": round((time.monotonic() - started) * 1000),
        }


def _clean_output(value: str | bytes, limit: int) -> str:
    if isinstance(value, bytes):
        value = value.decode("utf-8", errors="replace")
    cleaned = ANSI_RE.sub("", value).replace("\x00", "")
    if len(cleaned.encode("utf-8")) <= limit:
        return cleaned.strip()
    encoded = cleaned.encode("utf-8")[:limit]
    return encoded.decode("utf-8", errors="ignore").rstrip() + "\n[OUTPUT TRUNCATED]"


def _extract_workflow_output(output: str, definition: WorkflowDefinition) -> str:
    first = definition.commands[0] if definition.commands else ""
    marker = first.removeprefix("echo ").strip() if first.startswith("echo ") else ""
    if marker:
        position = output.rfind(marker)
        if position >= 0:
            return output[position:].strip()
    return output.strip()
