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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder

internal fun mirrorCandidate(repoId: String): String {
    val parts = repoId.split('/', limit = 2)
    val owner = parts.firstOrNull().orEmpty()
    val name = parts.getOrNull(1).orEmpty()
    val asset = URLEncoder.encode("${owner}__${name}.apk", "UTF-8")
    return "https://huggingface.co/datasets/livadies/frctl-mirror/resolve/main/$asset"
}

internal fun catalogHttpError(status: Int, github: Boolean, githubRateRemaining: String?): String? = when {
    status == 403 && github && githubRateRemaining == "0" -> "GitHub rate limit reached. Connect GitHub or use the cached catalog."
    status == 403 && github -> "GitHub refused access to this resource. Check the repository visibility and token permissions."
    status == 403 -> "Catalog source refused access to this resource."
    status == 429 -> "Catalog source rate limit reached. Use the cached catalog and retry later."
    status !in 200..299 -> "Network route returned HTTP $status"
    else -> null
}

data class CatalogPage(val entries: List<AppEntry>, val cached: Boolean, val cachedAt: Long? = null, val offline: Boolean = false)

data class LibraryState(val favoriteIds: Set<String>, val installedIds: Set<String>, val savedEntries: List<AppEntry>)

internal fun isCacheFresh(savedAt: Long, now: Long, ttl: Long): Boolean = savedAt <= now && now - savedAt < ttl

class MarketplaceRepository(private val context: Context) {
    private val store = TokenStore(context)
    private val dao = FrctlDatabase.get(context).dao()
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

    suspend fun discover(force: Boolean = false): CatalogPage = cachedSearch(
        key = "discover",
        query = "topic:android-app stars:>50 archived:false",
        sort = "stars",
        force = force
    )

    suspend fun trending(force: Boolean = false): CatalogPage = cachedSearch(
        key = "trending",
        query = "topic:android-app pushed:>2025-01-01 stars:>10 archived:false",
        sort = "updated",
        force = force
    )

    suspend fun models(force: Boolean = false): CatalogPage = cachedModels(force)

    fun library(): Flow<LibraryState> = dao.observeLibrary().map { entries ->
        LibraryState(
            favoriteIds = entries.filter { it.favorite }.mapTo(mutableSetOf(), LibraryEntity::entryKey),
            installedIds = entries.filter { it.installed }.mapTo(mutableSetOf(), LibraryEntity::entryKey),
            savedEntries = entries.map { entry ->
                AppEntry(
                    id = entry.entryId,
                    name = entry.name,
                    owner = entry.owner,
                    description = entry.description,
                    repoUrl = entry.repoUrl,
                    source = entry.source,
                    iconUrl = entry.iconUrl,
                    stars = entry.stars,
                    updatedAt = entry.latestUpdatedAt,
                    kind = EntryKind.valueOf(entry.kind),
                    category = MarketCategory.valueOf(entry.category),
                    downloads = entry.downloads,
                    pipelineTag = entry.pipelineTag,
                )
            },
        )
    }

    fun searchHistory(): Flow<List<String>> = dao.observeSearchHistory().map { rows -> rows.map(SearchHistoryEntity::query) }

    suspend fun recordSearch(query: String) {
        val normalized = query.trim().take(80)
        if (normalized.isBlank()) return
        dao.upsertSearch(SearchHistoryEntity(normalized, System.currentTimeMillis()))
        dao.trimSearchHistory()
    }

    suspend fun toggleFavorite(app: AppEntry) = toggleLibrary(app, favorite = true)

    suspend fun toggleInstalled(app: AppEntry) = toggleLibrary(app, favorite = false)

    suspend fun search(query: String): List<AppEntry> {
        if (query.isBlank()) return discover().entries
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

    private suspend fun cachedModels(force: Boolean): CatalogPage {
        val key = "models"
        val now = System.currentTimeMillis()
        memory[key]?.takeIf { !force && now - it.first < ttl }?.let { return CatalogPage(it.second, true, it.first) }
        val cached = dao.cache(key)
        if (!force && cached != null && isCacheFresh(cached.savedAt, now, ttl)) {
            val models = RawParsers.huggingFaceModels(cached.payload)
            if (models.isNotEmpty()) { memory[key] = cached.savedAt to models; return CatalogPage(models, true, cached.savedAt) }
        }
        return runCatching {
            val raw = request("https://huggingface.co/api/models?sort=trendingScore&direction=-1&limit=30", github = false)
            dao.upsertCache(CatalogCacheEntity(key, raw, now))
            val models = RawParsers.huggingFaceModels(raw)
            memory[key] = now to models
            CatalogPage(models, false, null)
        }.getOrElse {
            if (cached != null) CatalogPage(RawParsers.huggingFaceModels(cached.payload), true, cached.savedAt, offline = true) else throw it
        }
    }

    private suspend fun cachedSearch(key: String, query: String, sort: String, force: Boolean): CatalogPage {
        val now = System.currentTimeMillis()
        memory[key]?.takeIf { !force && now - it.first < ttl }?.let { return CatalogPage(it.second, true, it.first) }
        val cached = dao.cache(key)
        if (!force && cached != null && isCacheFresh(cached.savedAt, now, ttl)) {
            val apps = RawParsers.githubCandidates(cached.payload).map(::normalized)
            if (apps.isNotEmpty()) { memory[key] = cached.savedAt to apps; return CatalogPage(apps, true, cached.savedAt) }
        }
        return runCatching {
            val raw = request(searchUrl(query, sort))
            dao.upsertCache(CatalogCacheEntity(key, raw, now))
            val apps = RawParsers.githubCandidates(raw).map(::normalized)
            memory[key] = now to apps
            CatalogPage(apps, false, null)
        }.getOrElse {
            if (cached != null) CatalogPage(RawParsers.githubCandidates(cached.payload).map(::normalized), true, cached.savedAt, offline = true) else throw it
        }
    }

    private suspend fun toggleLibrary(app: AppEntry, favorite: Boolean) {
        val key = libraryKey(app)
        val existing = dao.libraryEntry(key)
        val updated = LibraryEntity(
            entryKey = key,
            entryId = app.id,
            kind = app.kind.name,
            name = app.name,
            owner = app.owner,
            description = app.description,
            repoUrl = app.repoUrl,
            source = app.source,
            iconUrl = app.iconUrl,
            stars = app.stars,
            downloads = app.downloads,
            pipelineTag = app.pipelineTag,
            category = app.category.name,
            latestUpdatedAt = existing?.latestUpdatedAt?.ifBlank { app.updatedAt } ?: app.updatedAt,
            favorite = if (favorite) !(existing?.favorite ?: false) else existing?.favorite ?: false,
            installed = if (favorite) existing?.installed ?: false else !(existing?.installed ?: false),
            checkedAt = existing?.checkedAt ?: 0L,
        )
        dao.upsertLibrary(updated)
        dao.deleteUnusedLibraryEntry(key)
    }

    internal suspend fun checkTrackedUpdates(): List<String> {
        val updated = mutableListOf<String>()
        dao.trackedEntries().forEach { entry ->
            if (entry.kind != EntryKind.ANDROID_APP.name) return@forEach
            runCatching {
                val raw = request("https://api.github.com/repos/${entry.entryId}/releases/latest")
                val latest = RawParsers.jsonString(raw, "published_at") ?: RawParsers.jsonString(raw, "created_at") ?: return@runCatching
                if (entry.checkedAt > 0 && entry.latestUpdatedAt.isNotBlank() && latest > entry.latestUpdatedAt) updated += entry.name
                dao.upsertLibrary(entry.copy(latestUpdatedAt = latest, checkedAt = System.currentTimeMillis()))
            }
        }
        return updated
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
        val rateRemaining = if (github) response.headers["X-RateLimit-Remaining"] else null
        catalogHttpError(response.status.value, github, rateRemaining)?.let(::error)
        return response.body()
    }
}

fun libraryKey(app: AppEntry): String = "${app.kind}:${app.id}"
