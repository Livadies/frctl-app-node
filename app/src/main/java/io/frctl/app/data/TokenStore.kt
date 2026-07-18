package io.frctl.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("frctl_secure_preferences")

private class AndroidKeystoreTokenCipher {
    private val alias = "frctl.github.token.aes.v1"
    private val prefix = "keystore:v1:"

    fun isEncrypted(value: String) = value.startsWith(prefix)

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return prefix + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        require(isEncrypted(value)) { "Unsupported encrypted token format" }
        val payload = Base64.decode(value.removePrefix(prefix), Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid encrypted token" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

class TokenStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("github_token")
    private val modeKey = stringPreferencesKey("token_mode")
    private val reauthKey = booleanPreferencesKey("github_token_requires_reauth")
    private val cipher = AndroidKeystoreTokenCipher()

    val token: Flow<String> = context.dataStore.data.map { preferences ->
        val stored = preferences[tokenKey].orEmpty()
        when {
            stored.isBlank() -> ""
            cipher.isEncrypted(stored) -> runCatching { cipher.decrypt(stored) }.getOrDefault("")
            else -> stored
        }
    }.distinctUntilChanged()

    val requiresReauth: Flow<Boolean> = context.dataStore.data.map { it[reauthKey] ?: false }.distinctUntilChanged()

    val mode: Flow<TokenMode> = context.dataStore.data.map {
        runCatching { TokenMode.valueOf(it[modeKey] ?: "BEARER") }.getOrDefault(TokenMode.BEARER)
    }

    suspend fun migrateLegacyTokenOnce() {
        val stored = context.dataStore.data.first()[tokenKey].orEmpty()
        val migration = runCatching {
            when {
                stored.isBlank() -> null
                cipher.isEncrypted(stored) -> cipher.decrypt(stored).let { stored }
                else -> cipher.encrypt(stored)
            }
        }
        context.dataStore.edit { current ->
            if (migration.isSuccess) {
                migration.getOrNull()?.let { migrated ->
                    if (current[tokenKey] == stored) current[tokenKey] = migrated
                }
                current[reauthKey] = false
            } else {
                current[reauthKey] = true
            }
        }
    }

    suspend fun save(token: String, mode: TokenMode) = context.dataStore.edit {
        val clean = token.trim()
        it[tokenKey] = if (clean.isBlank()) "" else cipher.encrypt(clean)
        it[modeKey] = mode.name
        it[reauthKey] = false
    }
}
