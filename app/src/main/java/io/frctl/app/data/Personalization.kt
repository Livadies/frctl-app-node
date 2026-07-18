package io.frctl.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.pow

private const val HALF_LIFE_MS = 7.0 * 24.0 * 60.0 * 60.0 * 1_000.0
private val Context.personalizationStore by preferencesDataStore("frctl_personalization")

enum class InteractionType { OPEN, FAVORITE, INSTALL }

class PersonalizationPreferences(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("personalization_enabled")
    val enabled: Flow<Boolean> = context.personalizationStore.data.map { it[enabledKey] ?: true }
    suspend fun setEnabled(value: Boolean) = context.personalizationStore.edit { it[enabledKey] = value }
}

internal fun categoryWeights(interactions: List<InteractionEntity>, now: Long): Map<MarketCategory, Double> {
    val raw = interactions.groupingBy { runCatching { MarketCategory.valueOf(it.category) }.getOrDefault(MarketCategory.TOOLS) }
        .fold(0.0) { total, item ->
            val eventWeight = when (runCatching { InteractionType.valueOf(item.eventType) }.getOrDefault(InteractionType.OPEN)) {
                InteractionType.OPEN -> 1.0
                InteractionType.FAVORITE -> 3.0
                InteractionType.INSTALL -> 4.0
            }
            val age = (now - item.timestamp).coerceAtLeast(0L).toDouble()
            total + eventWeight * 0.5.pow(age / HALF_LIFE_MS)
        }
    val maximum = raw.values.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyMap()
    return raw.mapValues { (_, value) -> value / maximum }
}

internal fun rankEntries(entries: List<AppEntry>, weights: Map<MarketCategory, Double>): List<AppEntry> {
    if (entries.size < 2 || weights.isEmpty()) return entries
    val denominator = (entries.size - 1).toDouble()
    return entries.withIndex()
        .sortedByDescending { (index, entry) ->
            val catalogScore = 1.0 - index / denominator
            0.7 * catalogScore + 0.3 * (weights[entry.category] ?: 0.0)
        }
        .map { it.value }
}
