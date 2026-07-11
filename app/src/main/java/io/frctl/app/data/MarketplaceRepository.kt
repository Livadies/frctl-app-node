package io.frctl.app.data

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first

class MarketplaceRepository(context: Context) {
    private val store = TokenStore(context)
    private val client = HttpClient(Android) { install(HttpTimeout) { requestTimeoutMillis = 15_000 } }

    suspend fun search(query: String): List<AppEntry> {
        val q = query.ifBlank { "android apk frctl" }
        val raw = request("https://api.github.com/search/repositories?q=${url("$q topic:android")}&sort=stars&per_page=20")
        val candidates = RawParsers.githubCandidates(raw)
        return candidates.take(12).mapNotNull { resolve(it) }.ifEmpty { mirrorSearch(q) }
    }

    suspend fun details(app: AppEntry): AppEntry {
        val readme = runCatching { request("https://raw.githubusercontent.com/${app.id}/HEAD/README.md") }.getOrDefault(app.description)
        return app.copy(readme = readme)
    }

    private suspend fun resolve(app: AppEntry): AppEntry? {
        val releaseRaw = runCatching { request("https://api.github.com/repos/${app.id}/releases/latest") }.getOrNull()
        val githubApk = releaseRaw?.let(RawParsers::apkLinks)?.firstOrNull()
        val mirror = mirrorUrl(app.name)
        return if (githubApk != null) app.copy(apkUrl = githubApk, mirrorUrl = mirror) else if (mirror != null) app.copy(apkUrl = mirror, mirrorUrl = mirror, source = "Hugging Face") else null
    }

    private suspend fun mirrorSearch(query: String): List<AppEntry> {
        val raw = runCatching { request("https://huggingface.co/api/datasets?search=${url("frctl-mirror $query")}&limit=20") }.getOrDefault("")
        return Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").findAll(raw).map { it.groupValues[1] }.map { id ->
            AppEntry(id, id.substringAfterLast('/'), id.substringBefore('/'), "FRCTL resilient mirror", "https://huggingface.co/datasets/$id", mirrorUrl = "https://huggingface.co/datasets/$id/resolve/main/frctl.apk", apkUrl = "https://huggingface.co/datasets/$id/resolve/main/frctl.apk", source = "Hugging Face")
        }.toList()
    }

    private suspend fun mirrorUrl(name: String): String? {
        val url = "https://huggingface.co/datasets/livadies/frctl-mirror/resolve/main/${name}.apk"
        return runCatching { client.get(url).status.value in 200..399 }.getOrDefault(false).let { if (it) url else null }
    }

    private suspend fun request(url: String): String {
        val token = store.token.first()
        val mode = store.mode.first()
        val response: HttpResponse = client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github.raw+json, application/json, text/plain")
            if (token.isNotBlank()) header(HttpHeaders.Authorization, when (mode) { TokenMode.BEARER -> "Bearer $token"; TokenMode.TOKEN -> "token $token"; TokenMode.RAW -> token })
        }
        return response.body()
    }

    private fun url(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
