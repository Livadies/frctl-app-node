package io.frctl.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalizationTest {
    @Test
    fun appliesSevenDayHalfLifeAndEventWeights() {
        val now = 20L * 24L * 60L * 60L * 1_000L
        val interactions = listOf(
            interaction(MarketCategory.AI, InteractionType.OPEN, now),
            interaction(MarketCategory.TOOLS, InteractionType.OPEN, now - 7L * 24L * 60L * 60L * 1_000L),
        )

        val weights = categoryWeights(interactions, now)

        assertEquals(1.0, weights.getValue(MarketCategory.AI), 0.0001)
        assertEquals(0.5, weights.getValue(MarketCategory.TOOLS), 0.0001)
    }

    @Test
    fun combinesCatalogOrderWithPrivateCategoryPreference() {
        val entries = listOf(
            entry("first", MarketCategory.TOOLS),
            entry("preferred", MarketCategory.AI),
            entry("third", MarketCategory.MEDIA),
            entry("fourth", MarketCategory.SECURITY),
        )

        val ranked = rankEntries(entries, mapOf(MarketCategory.AI to 1.0))

        assertEquals("preferred", ranked.first().id)
        assertEquals(entries, rankEntries(entries, emptyMap()))
    }

    private fun interaction(category: MarketCategory, event: InteractionType, timestamp: Long) = InteractionEntity(
        entryKey = "${category.name}:sample",
        kind = EntryKind.ANDROID_APP.name,
        category = category.name,
        eventType = event.name,
        timestamp = timestamp,
    )

    private fun entry(id: String, category: MarketCategory) = AppEntry(
        id = id,
        name = id,
        owner = "test",
        description = "",
        repoUrl = "https://example.invalid/$id",
        category = category,
    )
}
