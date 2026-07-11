package io.frctl.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.delay

data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUri: String, val interval: Int)

class GitHubDeviceAuth {
    private val client = HttpClient(Android)

    suspend fun begin(clientId: String): DeviceCode {
        val raw: String = client.submitForm(
            url = "https://github.com/login/device/code",
            formParameters = Parameters.build { append("client_id", clientId); append("scope", "read:user") }
        ) { header(HttpHeaders.Accept, "application/json") }.body()
        return DeviceCode(
            RawParsers.jsonString(raw, "device_code") ?: error("GitHub did not issue a device code"),
            RawParsers.jsonString(raw, "user_code") ?: error("Missing user code"),
            RawParsers.jsonString(raw, "verification_uri") ?: "https://github.com/login/device",
            Regex("\"interval\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 5
        )
    }

    suspend fun awaitToken(clientId: String, code: DeviceCode): String {
        repeat(60) {
            delay(code.interval.coerceAtLeast(5) * 1000L)
            val raw: String = client.submitForm(
                url = "https://github.com/login/oauth/access_token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("device_code", code.deviceCode)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }
            ) { header(HttpHeaders.Accept, "application/json") }.body()
            RawParsers.jsonString(raw, "access_token")?.let { return it }
            when (RawParsers.jsonString(raw, "error")) {
                "access_denied" -> error("Authorization was denied")
                "expired_token" -> error("The GitHub code expired")
                "slow_down" -> delay(5_000)
            }
        }
        error("GitHub authorization timed out")
    }
}
