from __future__ import annotations

import fnmatch
import ipaddress
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse


HOST_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
USER_RE = re.compile(r"^[A-Za-z0-9_.-]{1,32}$")
RUSTDESK_ID_RE = re.compile(r"^[A-Za-z0-9_-]{3,64}$")
DOCKER_IMAGE_RE = re.compile(
    r"^(?:[a-z0-9]+(?:[._-][a-z0-9]+)*(?::[0-9]+)?/)?"
    r"[a-z0-9]+(?:[._/-][a-z0-9]+)*(?::[A-Za-z0-9][A-Za-z0-9_.-]{0,127})?$"
)
KNOWN_FIELDS = {"connector", "action", "target", "client", "user", "image", "identity_file"}


class PolicyError(ValueError):
    pass


@dataclass(frozen=True)
class Target:
    host: str
    port: int | None
    scheme: str
    scope: str

    @property
    def display(self) -> str:
        return f"{self.host}:{self.port}" if self.port else self.host


def parse_target(raw: str, default_scheme: str = "ssh") -> Target:
    value = raw.strip()
    if not value or len(value) > 255:
        raise PolicyError("Цель должна содержать от 1 до 255 символов")
    if any(ch in value for ch in "\r\n\t @\\"):
        raise PolicyError("Credentials и пробельные символы в target запрещены")
    parsed = urlparse(value if "://" in value else f"{default_scheme}://{value}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise PolicyError("Credentials, query string и fragment в target запрещены")
    if parsed.scheme not in {"ssh", "https", "http"}:
        raise PolicyError("Неподдерживаемая схема target")
    host = (parsed.hostname or "").lower()
    if not HOST_RE.fullmatch(host):
        try:
            ipaddress.ip_address(host)
        except ValueError as exc:
            raise PolicyError("Target должен быть hostname или IP-адресом") from exc
    else:
        labels = host.split(".")
        if any(not label or len(label) > 63 or label.startswith("-") or label.endswith("-") for label in labels):
            raise PolicyError("Target содержит недопустимое имя хоста")
    try:
        port = parsed.port
    except ValueError as exc:
        raise PolicyError("Порт должен быть в диапазоне 1-65535") from exc
    if port is not None and not 1 <= port <= 65535:
        raise PolicyError("Порт должен быть в диапазоне 1-65535")
    scope = "hostname"
    try:
        ip = ipaddress.ip_address(host)
        scope = "private" if ip.is_private or ip.is_loopback else "public"
    except ValueError:
        if host == "localhost" or host.endswith(".local"):
            scope = "private"
        elif host.endswith(".example") or host.endswith(".test"):
            scope = "documentation"
    return Target(host=host, port=port, scheme=parsed.scheme, scope=scope)


def target_allowed(host: str, rules: list[str]) -> bool:
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = None
    for rule in rules:
        normalized = rule.strip().lower()
        if not normalized:
            continue
        if address is not None:
            try:
                if address in ipaddress.ip_network(normalized, strict=False):
                    return True
            except ValueError:
                pass
        if fnmatch.fnmatch(host.lower(), normalized):
            return True
    return False


def validate_common(payload: dict[str, Any]) -> tuple[str, str]:
    connector = str(payload.get("connector", ""))
    action = str(payload.get("action", "connect"))
    if connector not in {"ssh", "rustdesk", "docker", "browser"}:
        raise PolicyError("Неизвестный коннектор")
    if action not in {"connect", "open-workspace", "open"}:
        raise PolicyError("Действие не разрешено")
    forbidden = {"password", "token", "secret", "authorization", "cookie", "command", "args"}
    if forbidden.intersection(key.lower() for key in payload):
        raise PolicyError("Секреты и произвольные команды API не принимает")
    if set(payload) - KNOWN_FIELDS:
        raise PolicyError("API содержит неизвестные параметры")
    return connector, action


def validate_user(raw: Any) -> str:
    user = str(raw or "").strip()
    if user and not USER_RE.fullmatch(user):
        raise PolicyError("SSH user содержит недопустимые символы")
    return user


def validate_rustdesk_id(raw: Any) -> str:
    value = str(raw or "").strip()
    if not RUSTDESK_ID_RE.fullmatch(value):
        raise PolicyError("RustDesk ID должен содержать 3-64 буквы, цифры, _ или -")
    return value


def validate_docker_image(raw: Any) -> str:
    value = str(raw or "").strip()
    if not DOCKER_IMAGE_RE.fullmatch(value):
        raise PolicyError("Некорректное имя Docker image")
    return value
