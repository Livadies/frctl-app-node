from __future__ import annotations

import argparse
from pathlib import Path

from .config import NodeConfig
from .server import serve


def main() -> None:
    parser = argparse.ArgumentParser(description="FRCTL Node — локальный consent-gated launcher")
    parser.add_argument("--config", type=Path, help="Путь к node-config.json")
    parser.add_argument("--no-browser", action="store_true", help="Не открывать панель автоматически")
    args = parser.parse_args()
    config, path = NodeConfig.load(args.config)
    if args.no_browser:
        config.open_browser = False
    print(f"Конфигурация: {path}")
    serve(config)


if __name__ == "__main__":
    main()

