package io.frctl.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("frctl_secure_preferences")

class TokenStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("github_token")
    private val modeKey = stringPreferencesKey("token_mode")
    val token: Flow<String> = context.dataStore.data.map { it[tokenKey].orEmpty() }
    val mode: Flow<TokenMode> = context.dataStore.data.map { runCatching { TokenMode.valueOf(it[modeKey] ?: "BEARER") }.getOrDefault(TokenMode.BEARER) }
    suspend fun save(token: String, mode: TokenMode) = context.dataStore.edit { it[tokenKey] = token.trim(); it[modeKey] = mode.name }
}
