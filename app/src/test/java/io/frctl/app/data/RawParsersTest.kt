package io.frctl.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawParsersTest {
    @Test fun extractsOnlyStrictApkLinks() {
        val raw = """{"assets":[{"browser_download_url":"https://host/app.apk"},{"browser_download_url":"https://host/app.apks"},{"browser_download_url":"https:\/\/x\/mirror.apk"}]}"""
        assertEquals(listOf("https://host/app.apk", "https://x/mirror.apk"), RawParsers.apkLinks(raw))
    }
    @Test fun toleratesUnknownFields() {
        val raw = """{"items":[{"future":{"x":1},"description":"Demo with \"quotes\"","full_name":"dev/app","owner":{"avatar_url":"https://img/avatar.png"},"stargazers_count":null,"updated_at":"2026"}]}"""
        val app = RawParsers.githubCandidates(raw).single()
        assertEquals("dev/app", app.id)
        assertEquals("Demo with \"quotes\"", app.description)
        assertEquals("https://img/avatar.png", app.iconUrl)
        assertEquals(0, app.stars)
    }
    @Test fun parsesHuggingFaceModels() {
        val raw = """[{"id":"org/model-one","pipeline_tag":"text-generation","downloads":1200,"likes":42,"lastModified":"2026-07-16T12:00:00Z"}]"""
        val model = RawParsers.huggingFaceModels(raw).single()
        assertEquals(EntryKind.AI_MODEL, model.kind)
        assertEquals("org/model-one", model.id)
        assertEquals(1200, model.downloads)
        assertEquals(MarketCategory.AI, model.category)
    }
    @Test fun classifiesRemoteAccessApps() {
        assertEquals(MarketCategory.REMOTE_ACCESS, MarketplaceClassifier.android("Terminal", "SSH remote server client"))
    }
    @Test fun extractsPublishedSha256() {
        val hash = "a".repeat(64)
        val raw = """{"assets":[{"browser_download_url":"https://github.com/dev/app/releases/download/v1/app.apk.sha256"}],"body":"$hash  app.apk"}"""
        assertEquals(listOf("https://github.com/dev/app/releases/download/v1/app.apk.sha256"), RawParsers.sha256Links(raw))
        assertEquals(hash, RawParsers.sha256(raw))
    }
    @Test fun mirrorUsesOwnerAndOriginalRepoName() {
        val repository = mirrorCandidate("Some-Owner/My_App")
        assertEquals("https://huggingface.co/datasets/livadies/frctl-mirror/resolve/main/Some-Owner__My_App.apk", repository)
    }

    @Test fun parsesJsonScalarsWithoutRegex() {
        val raw = """{"interval":9,"user_code":"A\"B","unknown":true}"""
        assertEquals(9, RawParsers.jsonInt(raw, "interval"))
        assertEquals("A\"B", RawParsers.jsonString(raw, "user_code"))
    }

    @Test fun slowDownPermanentlyIncreasesPollingInterval() {
        var interval = nextDevicePollInterval(5, "slow_down")
        assertEquals(10, interval)
        interval = nextDevicePollInterval(interval, "authorization_pending")
        assertEquals(10, interval)
    }

    @Test fun distinguishesRateLimitFromOtherForbiddenResponses() {
        assertTrue(catalogHttpError(403, true, "0")!!.contains("rate limit"))
        assertTrue(catalogHttpError(403, true, "42")!!.contains("refused access"))
        assertTrue(catalogHttpError(403, false, null)!!.contains("Catalog source"))
        assertEquals(null, catalogHttpError(200, true, null))
    }

    @Test fun persistentCacheTtlRejectsStaleAndFutureRows() {
        assertTrue(isCacheFresh(savedAt = 1_000, now = 1_200, ttl = 300))
        assertEquals(false, isCacheFresh(savedAt = 1_000, now = 1_300, ttl = 300))
        assertEquals(false, isCacheFresh(savedAt = 1_301, now = 1_300, ttl = 300))
    }
}
