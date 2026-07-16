from __future__ import annotations

import json
import mimetypes
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

from . import __version__
from .core import CONNECTORS, RuntimeState, ValidationError, build_plan


STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
MAX_BODY = 16 * 1024
STATE = RuntimeState()


class Handler(BaseHTTPRequestHandler):
    server_version = "FRCTLHub"

    def log_message(self, fmt: str, *args: object) -> None:
        print(json.dumps({"level": "info", "message": fmt % args}))

    def end_headers(self) -> None:
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), usb=()")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
        )
        super().end_headers()

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/api/health":
            self._json({"status": "ok", "version": __version__, "execution": False})
        elif path == "/api/catalog":
            self._json({"connectors": CONNECTORS})
        elif path == "/api/policy":
            self._json(
                {
                    "execution": "disabled",
                    "telemetry": "explicit-consent-and-redaction",
                    "secrets": "never-accepted",
                    "storage": "ephemeral-memory",
                    "certification": "not-certified",
                }
            )
        elif path == "/api/status":
            self._json(STATE.snapshot())
        else:
            self._static(path)

    def do_POST(self) -> None:
        try:
            payload = self._read_json()
            path = urlparse(self.path).path
            if path == "/api/plan":
                response = build_plan(payload)
            elif path == "/api/telemetry":
                response = STATE.accept_event(payload)
            elif path == "/api/receipt":
                response = STATE.append_receipt(payload)
            else:
                self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
                return
            self._json(response, HTTPStatus.CREATED)
        except ValidationError as exc:
            self._json({"error": "validation_error", "message": str(exc)}, HTTPStatus.UNPROCESSABLE_ENTITY)
        except (json.JSONDecodeError, UnicodeDecodeError):
            self._json({"error": "invalid_json"}, HTTPStatus.BAD_REQUEST)
        except ValueError as exc:
            self._json({"error": "bad_request", "message": str(exc)}, HTTPStatus.BAD_REQUEST)

    def _read_json(self) -> dict:
        content_type = self.headers.get("Content-Type", "").split(";", 1)[0]
        if content_type != "application/json":
            raise ValueError("Content-Type must be application/json")
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_BODY:
            raise ValueError("Request body must be between 1 byte and 16 KiB")
        value = json.loads(self.rfile.read(length).decode("utf-8"))
        if not isinstance(value, dict):
            raise ValueError("JSON body must be an object")
        return value

    def _json(self, payload: dict, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _static(self, path: str) -> None:
        requested = "index.html" if path == "/" else path.lstrip("/")
        target = (STATIC_DIR / requested).resolve()
        if STATIC_DIR not in target.parents or not target.is_file():
            self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
            return
        body = target.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", mimetypes.guess_type(target.name)[0] or "application/octet-stream")
        self.send_header("Cache-Control", "public, max-age=300" if target.name != "index.html" else "no-cache")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def create_server(host: str = "0.0.0.0", port: int = 7860) -> ThreadingHTTPServer:
    return ThreadingHTTPServer((host, port), Handler)


def main() -> None:
    port = int(os.environ.get("PORT", "7860"))
    server = create_server(port=port)
    print(json.dumps({"event": "started", "port": port, "execution": False}))
    server.serve_forever()


if __name__ == "__main__":
    main()
