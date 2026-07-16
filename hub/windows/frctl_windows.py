from __future__ import annotations

import logging
import threading
from pathlib import Path

import webview

from frctl_node.config import NodeConfig, default_data_dir
from frctl_node.server import create_server


VERSION = "0.4.0"


def main() -> None:
    data_dir = default_data_dir()
    data_dir.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        filename=Path(data_dir) / "windows.log",
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )
    config, _ = NodeConfig.load()
    config.open_browser = False
    try:
        server, _ = create_server(config)
    except OSError as exc:
        logging.exception("Cannot start local FRCTL server")
        webview.create_window("FRCTL — ошибка запуска", html=f"<h2>FRCTL не запущен</h2><p>Локальный порт {config.port} уже занят.</p><p>{type(exc).__name__}</p>", width=560, height=360)
        webview.start(gui="edgechromium", private_mode=True)
        return

    thread = threading.Thread(target=server.serve_forever, name="frctl-loopback", daemon=True)
    thread.start()
    window = webview.create_window(
        f"FRCTL {VERSION}",
        f"http://{config.host}:{config.port}/",
        width=1440,
        height=920,
        min_size=(960, 640),
        background_color="#06100e",
    )

    def shutdown() -> None:
        server.shutdown()
        server.server_close()

    window.events.closed += shutdown
    logging.info("FRCTL Windows started on loopback")
    webview.start(gui="edgechromium", private_mode=True)


if __name__ == "__main__":
    main()
