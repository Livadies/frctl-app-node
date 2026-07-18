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
import java.io.File
import java.net.URLEncoder

internal fun mirrorCandidate(repoId: String): String {
    val parts = repoId.split('/', limit = 2)
    val owner = parts.firstOrNull().orEmpty()
    val name = parts.getOrNull(1).orEmpty()
    val asset = URLEncoder.encode("${owner}__${name}.apk", "UTF-8")
    return "https://huggingface.co/datasets/livadies/frctl-mirror/resolve/main/$asset"
}

class MarketplaceRepository(private val context: Context) {
    private val store = TokenStore(context)
    private val client = HttpClient(Android) { install(HttpTimeout) { requestTimeoutMillis = 20_000 } }
    private val memory = mutableMapOf<String, Pair<Long, List<AppEntry>>>()
    private val ttl = 5 * 60 * 1000L
    private val trustedPublishers by lazy {
        runCatching {
            val raw = context.assets.open("trusted_publishers.json").bufferedReader().use { it.readText() }
            val list = Regex("\\\"publishers\\\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL)).find(raw)?.groupValues?.get(1).orEmpty()
            Regex("\\\"([A-Za-z0-9_.-]+)\\\"").findAll(list).map { it.groupValues[1].lowercase() }.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun discover(force: Boolean = false): Pair<List<AppEntry>, Boolean> = cachedSearch(
        key = "discover",
        query = "topic:android-app stars:>50 archived:false",
        sort = "stars",
        force = force
    )

    suspend fun trending(force: Boolean = false): Pair<List<AppEntry>, Boolean> = cachedSearch(
        key = "trending",
        query = "topic:android-app pushed:>2025-01-01 stars:>10 archived:false",
        sort = "updated",
        force = force
    )

    suspend fun models(force: Boolean = false): Pair<List<AppEntry>, Boolean> = cachedModels(force)

    suspend fun search(query: String): List<AppEntry> {
        if (query.isBlank()) return discover().first
        val apps = fetchCandidates("$query in:name,description,readme android archived:false", "stars")
        val models = runCatching {
            RawParsers.huggingFaceModels(request("https://huggingface.co/api/models?search=${URLEncoder.encode(query, "UTF-8")}&sort=trendingScore&direction=-1&limit=20", github = false))
        }.getOrDefault(emptyList())
        return (apps + models).distinctBy { "${it.kind}:${it.id}" }
    }

    suspend fun details(app: AppEntry): AppEntry {
        if (app.kind == EntryKind.AI_MODEL) {
            val readme = runCatching { request("https://huggingface.co/${app.id}/raw/main/README.md", github = false) }
                .getOrDefault(app.description)
            return app.copy(readme = readme)
        }
        val release = runCatching { request("https://api.github.com/repos/${app.id}/releases/latest") }.getOrDefault("")
        val githubApk = RawParsers.apkLinks(release).firstOrNull()
        val checksumUrl = RawParsers.sha256Links(release).firstOrNull { checksum ->
            githubApk != null && checksum.substringAfterLast('/').removeSuffix(".sha256") == githubApk.substringAfterLast('/')
        } ?: RawParsers.sha256Links(release).firstOrNull()
        val expectedSha256 = checksumUrl?.let { url ->
            runCatching { RawParsers.sha256(request(url, github = false)) }.getOrNull()
        }
        val trusted = app.owner.lowercase() in trustedPublishers
        val verification = when {
            expectedSha256 != null && trusted -> ApkVerificationStatus.TRUSTED_CHECKSUM
            expectedSha256 != null -> ApkVerificationStatus.CHECKSUM_PUBLISHED
            trusted -> ApkVerificationStatus.TRUSTED_PUBLISHER
            else -> ApkVerificationStatus.UNVERIFIED
        }
        val mirror = if (githubApk == null) findMirror(app.id) else mirrorCandidate(app.id)
        val readme = runCatching { request("https://raw.githubusercontent.com/${app.id}/HEAD/README.md") }
            .getOrDefault(app.description)
        return app.copy(
            apkUrl = githubApk ?: mirror,
            mirrorUrl = mirror,
            readme = readme,
            source = if (githubApk != null) "GitHub" else if (mirror != null) "Hugging Face" else "GitHub",
            apkSha256 = expectedSha256,
            apkVerification = if (githubApk == null) ApkVerificationStatus.UNVERIFIED else verification,
            trustedPublisher = trusted
        )
    }

    private suspend fun cachedModels(force: Boolean): Pair<List<AppEntry>, Boolean> {
        val key = "models"
        val now = System.currentTimeMillis()
        memory[key]?.takeIf { !force && now - it.first < ttl }?.let { return it.second to true }
        val file = File(context.cacheDir, "catalog-$key.json")
        if (!force && file.exists() && now - file.lastModified() < ttl) {
            val models = RawParsers.huggingFaceModels(file.readText())
            if (models.isNotEmpty()) { memory[key] = now to models; return models to true }
        }
        return runCatching {
            val raw = request("https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=30", github = false)
            file.writeText(raw)
            val models = RawParsers.huggingFaceModels(raw)
            memory[key] = now to models
            models to false
        }.getOrElse {
            if (file.exists()) RawParsers.huggingFaceModels(file.readText()) to true else throw it
        }
    }

    private suspend fun cachedSearch(key: String, query: String, sort: String, force: Boolean): Pair<List<AppEntry>, Boolean> {
        val now = System.currentTimeMillis()
        memory[key]?.takeIf { !force && now - it.first < ttl }?.let { return it.second to true }
        val file = File(context.cacheDir, "catalog-$key.json")
        if (!force && file.exists() && now - file.lastModified() < ttl) {
            val apps = RawParsers.githubCandidates(file.readText()).map(::normalized)
            if (apps.isNotEmpty()) { memory[key] = now to apps; return apps to true }
        }
        return runCatching {
            val raw = request(searchUrl(query, sort))
            file.writeText(raw)
            val apps = RawParsers.githubCandidates(raw).map(::normalized)
            memory[key] = now to apps
            apps to false
        }.getOrElse {
            if (file.exists()) RawParsers.githubCandidates(file.readText()).map(::normalized) to true else throw it
        }
    }

    private suspend fun fetchCandidates(query: String, sort: String): List<AppEntry> =
        RawParsers.githubCandidates(request(searchUrl(query, sort))).map(::normalized)

    private fun normalized(app: AppEntry) = app.copy(
        name = app.name.replace('-', ' ').replace('_', ' '),
        description = app.description.replace(Regex("\\s+"), " ").trim().take(180)
    )

    private fun searchUrl(query: String, sort: String) =
        "https://api.github.com/search/repositories?q=${URLEncoder.encode(query, "UTF-8")}&sort=$sort&order=desc&per_page=30"

    private suspend fun findMirror(repoId: String): String? {
        val candidate = mirrorCandidate(repoId)
        return runCatching { client.get(candidate).status.value in 200..399 }.getOrDefault(false).let { if (it) candidate else null }
    }

    private suspend fun request(url: String, github: Boolean = true): String {
        val token = store.token.first()
        val mode = store.mode.first()
        val response: HttpResponse = client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github.raw+json, application/json, text/plain")
            if (github) header("X-GitHub-Api-Version", "2022-11-28")
            if (github && token.isNotBlank()) header(HttpHeaders.Authorization, when (mode) {
                TokenMode.BEARER -> "Bearer $token"
                TokenMode.TOKEN -> "token $token"
                TokenMode.RAW -> token
            })
        }
        if (response.status.value == 403 && github) error("GitHub rate limit reached. Connect GitHub or use the cached catalog.")
        if (response.status.value == 429) error("Catalog source rate limit reached. Use the cached catalog and retry later.")
        if (response.status.value !in 200..299) error("Network route returned HTTP ${response.status.value}")
        return response.body()
    }
}
