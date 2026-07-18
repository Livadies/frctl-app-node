import json
import tempfile
import unittest
from pathlib import Path

from frctl_node.marketplace import GITHUB_URL, MarketplaceService


class MarketplaceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp.cleanup()

    def test_normalizes_fixed_github_and_huggingface_sources(self):
        def fetch(url):
            if url == GITHUB_URL:
                return json.dumps({"items": [{"full_name": "org/ssh-client", "name": "ssh-client", "description": "Remote SSH terminal", "html_url": "https://github.com/org/ssh-client", "stargazers_count": 12, "updated_at": "2026-07-17T00:00:00Z"}]})
            return json.dumps([{"id": "org/model", "pipeline_tag": "text-generation", "downloads": 1200, "likes": 7, "lastModified": "2026-07-17T00:00:00Z"}])
        service = MarketplaceService(Path(self.temp.name) / "catalog.json", fetcher=fetch)
        result = service.snapshot()
        self.assertEqual({"GitHub", "Hugging Face"}, {item["source"] for item in result["entries"]})
        self.assertEqual("remote-access", result["entries"][0]["category"])
        self.assertEqual("ai-model", result["entries"][1]["kind"])

    def test_uses_disk_cache_when_sources_fail(self):
        cache = Path(self.temp.name) / "catalog.json"
        cached = {"schema": 1, "entries": [], "generated_at": "old"}
        cache.write_text(json.dumps(cached), encoding="utf-8")
        service = MarketplaceService(cache, fetcher=lambda _: (_ for _ in ()).throw(OSError("offline")))
        result = service.snapshot()
        self.assertTrue(result["cached"])
        self.assertTrue(result["degraded"])
        self.assertTrue(result["offline"])

    def test_returns_schema_one_empty_catalog_when_offline_without_cache(self):
        cache = Path(self.temp.name) / "missing" / "catalog.json"
        service = MarketplaceService(cache, fetcher=lambda _: (_ for _ in ()).throw(OSError("offline")))
        result = service.snapshot()
        self.assertEqual(1, result["schema"])
        self.assertEqual([], result["entries"])
        self.assertTrue(result["degraded"])
        self.assertTrue(result["offline"])


if __name__ == "__main__":
    unittest.main()
