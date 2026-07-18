from __future__ import annotations

import json
import threading
import time
import urllib.request
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Callable
from urllib.parse import quote, urlparse


GITHUB_SEARCH = "topic:android-app stars:>50 archived:false"
GITHUB_URL = f"https://api.github.com/search/repositories?q={quote(GITHUB_SEARCH)}&sort=updated&order=desc&per_page=24"
HF_URL = "https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=24"
CATEGORIES = ("all", "android", "ai", "security", "remote-access", "tools", "media")
MAX_RESPONSE = 2 * 1024 * 1024


def _text(value: Any, limit: int = 180) -> str:
    return " ".join(str(value or "").split())[:limit]


def _safe_url(value: Any, hosts: set[str]) -> str:
    url = str(value or "")
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in hosts or parsed.username or parsed.password:
        return ""
    return url[:500]


def _category(name: str, description: str) -> str:
    text = f"{name} {description}".lower()
    if any(word in text for word in ("ssh", "remote", "rdp", "vnc", "rustdesk", "server", "terminal")):
        return "remote-access"
    if any(word in text for word in ("security", "privacy", "vpn", "firewall", "password", "auth", "encrypt")):
        return "security"
    if any(word in text for word in ("camera", "music", "audio", "video", "photo", "gallery", "player")):
        return "media"
    if any(word in text for word in ("llm", "model", "machine learning", "neural", "chatbot", " ai ")):
        return "ai"
    return "tools"


def _default_fetch(url: str) -> str:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/vnd.github+json, application/json", "User-Agent": "FRCTL-Node/0.4"},
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        payload = response.read(MAX_RESPONSE + 1)
    if len(payload) > MAX_RESPONSE:
        raise ValueError("Marketplace response is too large")
    return payload.decode("utf-8")


class MarketplaceService:
    def __init__(self, cache_path: Path, *, fetcher: Callable[[str], str] | None = None, ttl_seconds: int = 300):
        self.cache_path = cache_path
        self.fetcher = fetcher or _default_fetch
        self.ttl_seconds = ttl_seconds
        self.lock = threading.Lock()
        self.memory: tuple[float, dict[str, Any]] | None = None

    def snapshot(self, force: bool = False) -> dict[str, Any]:
        with self.lock:
            now = time.time()
            if self.memory and not force and now - self.memory[0] < self.ttl_seconds:
                return {**self.memory[1], "cached": True}
            try:
                result = self._fetch()
                self.cache_path.parent.mkdir(parents=True, exist_ok=True)
                self.cache_path.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
                self.memory = (now, result)
                return result
            except Exception:
                if self.cache_path.is_file():
                    cached = json.loads(self.cache_path.read_text(encoding="utf-8"))
                    self.memory = (now, cached)
                    return {**cached, "cached": True, "degraded": True, "offline": True}
                empty = self._empty_snapshot()
                self.memory = (now, empty)
                return empty

    def _empty_snapshot(self) -> dict[str, Any]:
        return {
            "schema": 1,
            "generated_at": datetime.now(UTC).isoformat(timespec="seconds"),
            "cached": False,
            "degraded": True,
            "offline": True,
            "refresh_seconds": self.ttl_seconds,
            "categories": list(CATEGORIES),
            "sources": ["GitHub", "Hugging Face"],
            "entries": [],
        }

    def _fetch(self) -> dict[str, Any]:
        github = json.loads(self.fetcher(GITHUB_URL))
        models = json.loads(self.fetcher(HF_URL))
        entries = self._github_entries(github) + self._model_entries(models)
        return {
            "schema": 1,
            "generated_at": datetime.now(UTC).isoformat(timespec="seconds"),
            "cached": False,
            "degraded": False,
            "offline": False,
            "refresh_seconds": self.ttl_seconds,
            "categories": list(CATEGORIES),
            "sources": ["GitHub", "Hugging Face"],
            "entries": entries,
        }

    @staticmethod
    def _github_entries(payload: Any) -> list[dict[str, Any]]:
        items = payload.get("items", []) if isinstance(payload, dict) else []
        result = []
        for item in items[:24]:
            if not isinstance(item, dict):
                continue
            repo_id = _text(item.get("full_name"), 120)
            if "/" not in repo_id:
                continue
            name = _text(item.get("name"), 80)
            description = _text(item.get("description")) or "Open-source Android application"
            url = _safe_url(item.get("html_url"), {"github.com"})
            if not url:
                continue
            result.append({
                "id": f"github:{repo_id}", "kind": "android", "name": name, "owner": repo_id.split("/", 1)[0],
                "description": description, "category": _category(name, description), "source": "GitHub", "url": url,
                "score": int(item.get("stargazers_count") or 0), "downloads": 0, "updated_at": _text(item.get("updated_at"), 40),
            })
        return result

    @staticmethod
    def _model_entries(payload: Any) -> list[dict[str, Any]]:
        result = []
        for item in payload[:24] if isinstance(payload, list) else []:
            if not isinstance(item, dict):
                continue
            model_id = _text(item.get("id") or item.get("modelId"), 120)
            if "/" not in model_id:
                continue
            url = _safe_url(f"https://huggingface.co/{model_id}", {"huggingface.co"})
            pipeline = _text(item.get("pipeline_tag"), 80) or "model"
            result.append({
                "id": f"hf:{model_id}", "kind": "ai-model", "name": model_id.split("/", 1)[1],
                "owner": model_id.split("/", 1)[0], "description": f"Hugging Face · {pipeline.replace('-', ' ')}",
                "category": "ai", "source": "Hugging Face", "url": url, "score": int(item.get("likes") or 0),
                "downloads": int(item.get("downloads") or 0), "updated_at": _text(item.get("lastModified"), 40),
            })
        return result
