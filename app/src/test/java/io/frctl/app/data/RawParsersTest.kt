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
}
