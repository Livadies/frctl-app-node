from __future__ import annotations

import json
import mimetypes
import os
import secrets
import threading
import webbrowser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from . import __version__
from .audit import AuditLog
from .config import NodeConfig, default_data_dir
from .confirmation import Confirmer, NativeConfirmer
from .connectors import ConnectorRegistry, ProcessLauncher
from .policy import PolicyError
from .marketplace import MarketplaceService
from .workflows import WorkflowExecutionError, WorkflowExecutor, WorkflowRegistry


STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
MAX_BODY = 32 * 1024


class NodeService:
    def __init__(
        self,
        config: NodeConfig,
        *,
        confirmer: Confirmer | None = None,
        launcher: ProcessLauncher | None = None,
        workflow_executor: WorkflowExecutor | None = None,
        audit: AuditLog | None = None,
        marketplace: MarketplaceService | None = None,
    ):
        self.config = config
        self.registry = ConnectorRegistry(config)
        self.confirmer = confirmer or NativeConfirmer()
        self.launcher = launcher or ProcessLauncher()
        self.workflows = WorkflowRegistry(config)
        self.workflow_executor = workflow_executor or WorkflowExecutor(config)
        self.audit = audit or AuditLog(default_data_dir() / "node-audit.jsonl", config.audit_limit)
        self.marketplace = marketplace or MarketplaceService(default_data_dir() / "marketplace-cache.json")
        self.session = secrets.token_urlsafe(32)

    def status(self) -> dict[str, Any]:
        confirmation_available = bool(getattr(self.confirmer, "available", True))
        identity_files = []
        for item in self.config.allowed_identity_files:
            path = Path(os.path.expandvars(item)).expanduser()
            if path.is_file():
                identity_files.append({"path": str(path), "name": path.name})
        return {
            "status": "ready",
            "version": __version__,
            "listen": f"http://{self.config.host}:{self.config.port}",
            "loopback_only": True,
            "native_confirmation": confirmation_available,
            "confirmation_fail_closed": not confirmation_available,
            "confirmation_failure": getattr(self.confirmer, "failure_reason", None),
            "arbitrary_commands": False,
            "audit_verified": self.audit.verify(),
            "connectors": self.registry.capabilities(),
            "allowed_targets": self.config.allowed_targets,
            "allowed_docker_images": self.config.allowed_docker_images,
            "identity_files": identity_files,
            "workflows": self.workflows.catalog(),
            "plink_ready": self.workflows.plink_ready(),
            "pinned_targets": list(self.config.ssh_host_keys),
            "ssh_users": self.config.ssh_users,
        }

    def plan(self, payload: dict[str, Any]) -> dict[str, Any]:
        return self.registry.plan(payload).public()

    def launch(self, payload: dict[str, Any]) -> dict[str, Any]:
        plan = self.registry.plan(payload)
        if not plan.installed:
            self.audit.append(connector=plan.connector, action=plan.action, target=plan.target, result="client-missing", command=plan.command)
            raise FileNotFoundError(f"Клиент не установлен: {plan.executable}")
        message = "\n".join(
            [
                plan.title,
                "",
                *[f"• {control}" for control in plan.controls],
                "",
                "Разрешить однократный запуск?",
            ]
        )
        if not self.confirmer.confirm("FRCTL Node — подтверждение", message):
            self.audit.append(connector=plan.connector, action=plan.action, target=plan.target, result="denied", command=plan.command)
            return {"launched": False, "result": "denied", "plan": plan.public()}
        try:
            pid = self.launcher.launch(plan)
        except Exception:
            self.audit.append(connector=plan.connector, action=plan.action, target=plan.target, result="launch-error", command=plan.command)
            raise
        receipt = self.audit.append(connector=plan.connector, action=plan.action, target=plan.target, result="launched", command=plan.command)
        return {"launched": True, "result": "launched", "pid": pid, "receipt": receipt["hash"], "plan": plan.public()}

    def workflow_plan(self, payload: dict[str, Any]) -> dict[str, Any]:
        return self.workflows.plan(payload).public()

    def workflow_run(self, payload: dict[str, Any]) -> dict[str, Any]:
        plan = self.workflows.plan(payload)
        if not plan.installed:
            self.audit.append(connector="ssh-workflow", action=plan.definition.id, target=plan.target, result="client-missing", command=plan.command)
            raise FileNotFoundError("Plink не установлен")
        message = "\n".join(
            [
                f"{plan.definition.name} → {plan.user}@{plan.target}",
                "",
                f"Host key: {plan.host_key}",
                f"Identity: {Path(plan.identity_file).name}",
                f"Изменяет сервер: {'ДА — только фиксированный /tmp файл' if plan.definition.mutating else 'НЕТ'}",
                "",
                *[f"• {command}" for command in plan.definition.commands],
                "",
                "Разрешить однократное выполнение фиксированного workflow?",
            ]
        )
        if not self.confirmer.confirm("FRCTL Node — SSH workflow", message):
            self.audit.append(connector="ssh-workflow", action=plan.definition.id, target=plan.target, result="denied", command=[*plan.command, *plan.definition.commands])
            return {"executed": False, "result": "denied", "plan": plan.public()}
        try:
            execution = self.workflow_executor.run(plan)
        except WorkflowExecutionError as exc:
            self.audit.append(
                connector="ssh-workflow",
                action=plan.definition.id,
                target=plan.target,
                result="workflow-error",
                command=[*plan.command, *plan.definition.commands],
                evidence=exc.output,
            )
            raise
        receipt = self.audit.append(
            connector="ssh-workflow",
            action=plan.definition.id,
            target=plan.target,
            result="executed",
            command=[*plan.command, *plan.definition.commands],
            evidence=execution["output"],
        )
        return {
            "executed": True,
            "result": "executed",
            "output": execution["output"],
            "returncode": execution["returncode"],
            "duration_ms": execution["duration_ms"],
            "receipt": receipt["hash"],
            "plan": plan.public(),
        }


def handler_factory(service: NodeService):
    class Handler(BaseHTTPRequestHandler):
        server_version = "FRCTLNode"

        def log_message(self, fmt: str, *args: object) -> None:
            print(json.dumps({"level": "info", "message": fmt % args}, ensure_ascii=False))

        def end_headers(self) -> None:
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("X-Frame-Options", "DENY")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), usb=(), payment=()")
            self.send_header(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
            )
            super().end_headers()

        def do_OPTIONS(self) -> None:
            self._json({"error": "cross_origin_requests_disabled"}, HTTPStatus.METHOD_NOT_ALLOWED)

        def do_GET(self) -> None:
            path = urlparse(self.path).path
            if not self._valid_host():
                self._json({"error": "invalid_host"}, HTTPStatus.MISDIRECTED_REQUEST)
                return
            if path == "/":
                self._static("index.html", set_session=True)
                return
            if path.startswith("/api/") and not self._authenticated():
                self._json({"error": "unauthorized"}, HTTPStatus.UNAUTHORIZED)
                return
            if path == "/api/status":
                self._json(service.status())
            elif path == "/api/audit":
                self._json({"verified": service.audit.verify(), "records": service.audit.recent()})
            elif path == "/api/audit/stream":
                self._audit_stream()
            elif path == "/api/audit/export":
                self._audit_export()
            elif path == "/api/marketplace":
                self._json(service.marketplace.snapshot())
            elif path.startswith("/api/"):
                self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
            else:
                self._static(path.lstrip("/"))

        def do_POST(self) -> None:
            if not self._authenticated() or not self._same_origin():
                self._json({"error": "unauthorized"}, HTTPStatus.UNAUTHORIZED)
                return
            try:
                payload = self._read_json()
                path = urlparse(self.path).path
                if path == "/api/plan":
                    response = service.plan(payload)
                    status = HTTPStatus.OK
                elif path == "/api/launch":
                    response = service.launch(payload)
                    status = HTTPStatus.OK
                elif path == "/api/workflow/plan":
                    response = service.workflow_plan(payload)
                    status = HTTPStatus.OK
                elif path == "/api/workflow/run":
                    response = service.workflow_run(payload)
                    status = HTTPStatus.OK
                else:
                    self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
                    return
                self._json(response, status)
            except PolicyError as exc:
                self._json({"error": "policy_denied", "message": str(exc)}, HTTPStatus.UNPROCESSABLE_ENTITY)
            except FileNotFoundError as exc:
                self._json({"error": "client_missing", "message": str(exc)}, HTTPStatus.CONFLICT)
            except WorkflowExecutionError as exc:
                self._json(
                    {"error": "workflow_failed", "message": str(exc), "output": exc.output, "returncode": exc.returncode},
                    HTTPStatus.BAD_GATEWAY,
                )
            except (json.JSONDecodeError, UnicodeDecodeError):
                self._json({"error": "invalid_json"}, HTTPStatus.BAD_REQUEST)
            except ValueError as exc:
                self._json({"error": "bad_request", "message": str(exc)}, HTTPStatus.BAD_REQUEST)
            except Exception:
                self._json({"error": "launch_failed", "message": "Клиент не удалось запустить; подробность записана локально"}, HTTPStatus.INTERNAL_SERVER_ERROR)

        def _expected_hosts(self) -> set[str]:
            return {f"127.0.0.1:{service.config.port}", f"localhost:{service.config.port}"}

        def _valid_host(self) -> bool:
            return self.headers.get("Host", "").lower() in self._expected_hosts()

        def _same_origin(self) -> bool:
            if self.headers.get("X-FRCTL-Request") != "1":
                return False
            host = self.headers.get("Host", "").lower()
            origin = self.headers.get("Origin", "").lower()
            return host in self._expected_hosts() and origin in {f"http://{item}" for item in self._expected_hosts()}

        def _authenticated(self) -> bool:
            if not self._valid_host():
                return False
            cookie = self.headers.get("Cookie", "")
            values = {}
            for item in cookie.split(";"):
                if "=" in item:
                    key, value = item.strip().split("=", 1)
                    values[key] = value
            return secrets.compare_digest(values.get("frctl_session", ""), service.session)

        def _read_json(self) -> dict[str, Any]:
            if self.headers.get("Content-Type", "").split(";", 1)[0] != "application/json":
                raise ValueError("Content-Type должен быть application/json")
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_BODY:
                raise ValueError("JSON body должен быть от 1 байта до 32 KiB")
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(payload, dict):
                raise ValueError("JSON body должен быть объектом")
            return payload

        def _json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _audit_export(self) -> None:
            try:
                body = service.audit.export_bytes()
            except RuntimeError as exc:
                self._json({"error": "audit_broken", "message": str(exc)}, HTTPStatus.CONFLICT)
                return
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
            self.send_header("Content-Disposition", 'attachment; filename="frctl-audit.jsonl"')
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _audit_stream(self) -> None:
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            records = service.audit.recent()
            last_hash = str(records[-1].get("hash", "")) if records else ""
            try:
                while True:
                    payload = json.dumps({"verified": service.audit.verify(), "records": records}, ensure_ascii=False)
                    self.wfile.write(f"event: audit\ndata: {payload}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    records = service.audit.wait_for_change(last_hash, timeout=15.0)
                    if records:
                        last_hash = str(records[-1].get("hash", ""))
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, TimeoutError):
                self.close_connection = True
                return

        def _static(self, name: str, set_session: bool = False) -> None:
            target = (STATIC_DIR / name).resolve()
            if STATIC_DIR not in target.parents or not target.is_file():
                self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
                return
            body = target.read_bytes()
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", mimetypes.guess_type(target.name)[0] or "application/octet-stream")
            self.send_header("Cache-Control", "no-cache")
            if set_session:
                self.send_header("Set-Cookie", f"frctl_session={service.session}; HttpOnly; SameSite=Strict; Path=/")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    return Handler


def create_server(config: NodeConfig, **service_kwargs: Any) -> tuple[ThreadingHTTPServer, NodeService]:
    service = NodeService(config, **service_kwargs)
    server = ThreadingHTTPServer((config.host, config.port), handler_factory(service))
    return server, service


def serve(config: NodeConfig) -> None:
    server, _ = create_server(config)
    url = f"http://{config.host}:{config.port}/"
    print(f"FRCTL Node {__version__}: {url}")
    print("Остановка: Ctrl+C")
    if config.open_browser:
        threading.Timer(0.5, lambda: webbrowser.open_new_tab(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
