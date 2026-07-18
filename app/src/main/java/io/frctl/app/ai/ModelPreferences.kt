package io.frctl.app.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelPreferencesStore by preferencesDataStore("frctl_model_preferences")

class ModelPreferences(private val context: Context) {
    private val unmeteredKey = booleanPreferencesKey("models_unmetered_only")

    val unmeteredOnly: Flow<Boolean> = context.modelPreferencesStore.data.map { it[unmeteredKey] ?: true }

    suspend fun setUnmeteredOnly(value: Boolean) = context.modelPreferencesStore.edit { it[unmeteredKey] = value }
}
