package io.frctl.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketplaceSecurityTest {
    @Test
    fun trustedPublishersUseStructuredJson() {
        val publishers = parseTrustedPublishers(
            """{"publishers":["F-Droid","GuardianProject"],"ignored":{"publishers":["attacker"]}}"""
        )

        assertEquals(setOf("f-droid", "guardianproject"), publishers)
    }

    @Test
    fun checksumMustMatchApkFilenameExactly() {
        val apk = "https://example.test/download/frctl.apk"
        val unrelated = "https://example.test/download/other.apk.sha256"
        val exact = "https://example.test/download/frctl.apk.sha256"

        assertEquals(exact, matchingChecksumUrl(apk, listOf(unrelated, exact)))
        assertNull(matchingChecksumUrl(apk, listOf(unrelated)))
        assertNull(matchingChecksumUrl(null, listOf(exact)))
    }
}
