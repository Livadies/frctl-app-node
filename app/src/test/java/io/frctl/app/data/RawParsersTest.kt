package io.frctl.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RawParsersTest {
    @Test fun extractsOnlyStrictApkLinks() {
        val raw = """{"url":"https://host/app.apk","bad":"https://host/app.apks","escaped":"https:\/\/x\/mirror.apk"}"""
        assertEquals(listOf("https://host/app.apk", "https://x/mirror.apk"), RawParsers.apkLinks(raw))
    }
    @Test fun toleratesUnknownFields() {
        val raw = """{"items":[{"noise":7,"full_name":"dev/app","description":"Demo","html_url":"https://github.com/dev/app","future":{"x":1}}]}"""
        assertEquals("dev/app", RawParsers.githubCandidates(raw).single().id)
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
        val raw = """{"browser_download_url":"https://github.com/dev/app/releases/download/v1/app.apk.sha256","body":"$hash  app.apk"}"""
        assertEquals(listOf("https://github.com/dev/app/releases/download/v1/app.apk.sha256"), RawParsers.sha256Links(raw))
        assertEquals(hash, RawParsers.sha256(raw))
    }
    @Test fun mirrorUsesOwnerAndOriginalRepoName() {
        val repository = mirrorCandidate("Some-Owner/My_App")
        assertEquals("https://huggingface.co/datasets/livadies/frctl-mirror/resolve/main/Some-Owner__My_App.apk", repository)
    }
}
