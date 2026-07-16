from __future__ import annotations

import os
import shutil
import subprocess
import webbrowser
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

from .config import NodeConfig
from .policy import PolicyError, parse_target, target_allowed, validate_common, validate_docker_image, validate_rustdesk_id, validate_user


@dataclass(frozen=True)
class LaunchPlan:
    connector: str
    action: str
    target: str
    title: str
    command: list[str]
    executable: str
    installed: bool
    controls: list[str]

    def public(self) -> dict[str, Any]:
        preview: list[str] = []
        for index, argument in enumerate(self.command):
            if index == 0 or (index > 0 and self.command[index - 1] == "-i"):
                preview.append(Path(argument).name)
            else:
                preview.append(argument)
        return {
            "connector": self.connector,
            "action": self.action,
            "target": self.target,
            "title": self.title,
            "installed": self.installed,
            "command_preview": preview,
            "controls": self.controls,
            "requires_native_confirmation": True,
        }


def _first_executable(command: str, candidates: list[str]) -> str | None:
    found = shutil.which(command)
    if found:
        return found
    expanded = (Path(os.path.expandvars(item)).expanduser() for item in candidates)
    return next((str(item) for item in expanded if item.is_file()), None)


def _docker_daemon_ready(executable: str | None) -> bool:
    if not executable:
        return False
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    try:
        result = subprocess.run(
            [executable, "version", "--format", "{{.Server.Version}}"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=2,
            creationflags=flags,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0


class ConnectorRegistry:
    def __init__(self, config: NodeConfig):
        self.config = config

    def capabilities(self) -> list[dict[str, Any]]:
        ssh = shutil.which("ssh")
        putty = _first_executable("putty", self.config.putty_paths)
        rustdesk = _first_executable("rustdesk", self.config.rustdesk_paths)
        docker = shutil.which("docker")
        docker_ready = _docker_daemon_ready(docker)
        return [
            {"id": "ssh", "name": "SSH / PuTTY", "installed": bool(ssh or putty), "providers": {"openssh": bool(ssh), "putty": bool(putty)}},
            {"id": "rustdesk", "name": "RustDesk", "installed": bool(rustdesk), "providers": {"rustdesk": bool(rustdesk)}},
            {"id": "docker", "name": "Docker Sandbox", "installed": docker_ready, "providers": {"docker_cli": bool(docker), "docker_daemon": docker_ready}},
            {"id": "browser", "name": "Browser Workspace", "installed": True, "providers": {"system_browser": True}},
        ]

    def plan(self, payload: dict[str, Any]) -> LaunchPlan:
        connector, action = validate_common(payload)
        if connector == "ssh":
            return self._ssh(payload, action)
        if connector == "rustdesk":
            return self._rustdesk(payload, action)
        if connector == "docker":
            return self._docker(payload, action)
        return self._browser(payload, action)

    def _allowed_target(self, raw: Any, scheme: str = "ssh"):
        target = parse_target(str(raw or ""), scheme)
        if not target_allowed(target.host, self.config.allowed_targets):
            raise PolicyError("Target не входит в allowlist. Добавьте hostname или CIDR в node-config.json")
        return target

    def _identity_file(self, raw: Any, client: str) -> str | None:
        value = str(raw or "").strip()
        if not value:
            return None
        if client != "putty":
            raise PolicyError("PPK identity разрешён только для PuTTY")
        candidate = Path(os.path.expandvars(value)).expanduser()
        try:
            resolved = candidate.resolve(strict=True)
        except OSError as exc:
            raise PolicyError("Identity file не найден") from exc
        allowed: set[str] = set()
        for item in self.config.allowed_identity_files:
            try:
                allowed.add(os.path.normcase(str(Path(os.path.expandvars(item)).expanduser().resolve(strict=True))))
            except OSError:
                continue
        if os.path.normcase(str(resolved)) not in allowed:
            raise PolicyError("Identity file отсутствует в allowlist")
        if resolved.suffix.lower() != ".ppk":
            raise PolicyError("PuTTY принимает только разрешённый PPK identity")
        return str(resolved)

    def _ssh(self, payload: dict[str, Any], action: str) -> LaunchPlan:
        target = self._allowed_target(payload.get("target"), "ssh")
        user = validate_user(payload.get("user"))
        client = str(payload.get("client", "openssh"))
        identity_file = self._identity_file(payload.get("identity_file"), client)
        port = target.port or 22
        destination = f"{user}@{target.host}" if user else target.host
        putty = _first_executable("putty", self.config.putty_paths)
        openssh = shutil.which("ssh")
        if client == "putty":
            executable = putty or "putty.exe"
            command = [executable, "-ssh", destination, "-P", str(port)]
            if identity_file:
                command.extend(["-i", identity_file])
            installed = bool(putty)
        elif client == "openssh":
            executable = openssh or "ssh"
            command = [
                executable,
                "-p",
                str(port),
                "-o",
                "StrictHostKeyChecking=ask",
                "-o",
                "ConnectionAttempts=1",
                "-o",
                "ServerAliveInterval=30",
                "-o",
                "ServerAliveCountMax=3",
                destination,
            ]
            installed = bool(openssh)
        else:
            raise PolicyError("Разрешены только OpenSSH и PuTTY")
        controls = ["Target прошёл allowlist", "Пароль отсутствует в API и command line", "Host key подтверждается OpenSSH/PuTTY", "Запуск требует локального подтверждения"]
        if identity_file:
            controls.insert(1, "PPK identity существует и входит в локальный allowlist")
        return LaunchPlan(
            connector="ssh",
            action=action,
            target=target.display,
            title=f"SSH → {destination}:{port}",
            command=command,
            executable=executable,
            installed=installed,
            controls=controls,
        )

    def _rustdesk(self, payload: dict[str, Any], action: str) -> LaunchPlan:
        peer_id = validate_rustdesk_id(payload.get("target"))
        executable = _first_executable("rustdesk", self.config.rustdesk_paths) or "rustdesk.exe"
        return LaunchPlan(
            connector="rustdesk",
            action=action,
            target=peer_id,
            title=f"RustDesk → {peer_id}",
            command=[executable, "--connect", peer_id],
            executable=executable,
            installed=Path(executable).is_file() or bool(shutil.which(executable)),
            controls=["Пароль не передаётся через command line", "Авторизация выполняется в RustDesk", "Входящее подтверждение управляется RustDesk", "Запуск требует локального подтверждения"],
        )

    def _docker(self, payload: dict[str, Any], action: str) -> LaunchPlan:
        image = validate_docker_image(payload.get("image", self.config.allowed_docker_images[0]))
        if image not in self.config.allowed_docker_images:
            raise PolicyError("Docker image отсутствует в allowlist")
        executable = shutil.which("docker") or "docker"
        command = [
            executable,
            "run",
            "--rm",
            "--interactive",
            "--tty",
            "--read-only",
            "--cap-drop=ALL",
            "--security-opt=no-new-privileges",
            "--pids-limit=128",
            "--memory=512m",
            "--cpus=1",
            "--network=none",
            "--tmpfs=/tmp:rw,noexec,nosuid,size=64m",
            image,
            "/bin/sh",
        ]
        return LaunchPlan(
            connector="docker",
            action=action,
            target=image,
            title=f"Изолированная песочница → {image}",
            command=command,
            executable=executable,
            installed=_docker_daemon_ready(shutil.which("docker")),
            controls=["Нет сети", "Нет host mounts", "Read-only root filesystem", "Все Linux capabilities удалены", "Лимиты CPU/RAM/PID", "Запуск требует локального подтверждения"],
        )

    def _browser(self, payload: dict[str, Any], action: str) -> LaunchPlan:
        target = self._allowed_target(payload.get("target"), "https")
        scheme = target.scheme if target.scheme in {"http", "https"} else "https"
        url = f"{scheme}://{target.display}"
        return LaunchPlan(
            connector="browser",
            action=action,
            target=url,
            title=f"Браузер → {url}",
            command=["system-browser", url],
            executable="system-browser",
            installed=True,
            controls=["URL прошёл allowlist", "Credentials отсутствуют", "Используется профиль браузера пользователя", "Запуск требует локального подтверждения"],
        )


class ProcessLauncher:
    def launch(self, plan: LaunchPlan) -> int:
        if not plan.installed:
            raise FileNotFoundError(f"Клиент не установлен: {plan.executable}")
        if plan.connector == "browser":
            if not webbrowser.open_new_tab(plan.target):
                raise RuntimeError("Системный браузер не принял URL")
            return 0
        flags = subprocess.CREATE_NEW_CONSOLE if os.name == "nt" else 0
        process = subprocess.Popen(plan.command, creationflags=flags, close_fds=True)
        return int(process.pid)
