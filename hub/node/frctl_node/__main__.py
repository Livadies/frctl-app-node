from __future__ import annotations

import argparse
from pathlib import Path

from .audit import AuditLog
from .config import NodeConfig, default_data_dir
from .server import serve


def main() -> None:
    parser = argparse.ArgumentParser(description="FRCTL Node — локальный consent-gated launcher")
    parser.add_argument("--config", type=Path, help="Путь к node-config.json")
    parser.add_argument("--no-browser", action="store_true", help="Не открывать панель автоматически")
    parser.add_argument("--verify-audit", action="store_true", help="Проверить HMAC-целостность журнала и завершить работу")
    args = parser.parse_args()
    if args.verify_audit:
        audit = AuditLog(default_data_dir() / "node-audit.jsonl")
        verified = audit.verify()
        print("AUDIT VERIFIED" if verified else "AUDIT VERIFICATION FAILED")
        raise SystemExit(0 if verified else 1)
    config, path = NodeConfig.load(args.config)
    if args.no_browser:
        config.open_browser = False
    print(f"Конфигурация: {path}")
    serve(config)


if __name__ == "__main__":
    main()

