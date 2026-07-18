package io.frctl.app.data

enum class EntryKind { ANDROID_APP, AI_MODEL }

enum class MarketCategory { ALL, ANDROID, AI, SECURITY, REMOTE_ACCESS, TOOLS, MEDIA }

enum class ApkVerificationStatus { TRUSTED_CHECKSUM, CHECKSUM_PUBLISHED, TRUSTED_PUBLISHER, UNVERIFIED }

data class AppEntry(
    val id: String,
    val name: String,
    val owner: String,
    val description: String,
    val repoUrl: String,
    val apkUrl: String? = null,
    val mirrorUrl: String? = null,
    val readme: String = "",
    val source: String = "GitHub",
    val iconUrl: String? = null,
    val stars: Int = 0,
    val updatedAt: String = "",
    val kind: EntryKind = EntryKind.ANDROID_APP,
    val category: MarketCategory = MarketCategory.TOOLS,
    val downloads: Int = 0,
    val pipelineTag: String = "",
    val apkSha256: String? = null,
    val apkVerification: ApkVerificationStatus = ApkVerificationStatus.UNVERIFIED,
    val trustedPublisher: Boolean = false
)

enum class TokenMode { BEARER, TOKEN, RAW }

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val error: String? = null,
    val selected: AppEntry? = null,
    val featured: List<AppEntry> = emptyList(),
    val trending: List<AppEntry> = emptyList(),
    val models: List<AppEntry> = emptyList(),
    val cached: Boolean = false,
    val offline: Boolean = false,
    val cachedAt: Long? = null,
    val category: MarketCategory = MarketCategory.ALL,
    val lastUpdatedAt: Long = 0L,
    val favoriteIds: Set<String> = emptySet(),
    val installedIds: Set<String> = emptySet(),
    val libraryEntries: List<AppEntry> = emptyList(),
    val searchHistory: List<String> = emptyList(),
)

object MarketplaceClassifier {
    fun android(name: String, description: String): MarketCategory {
        val text = "$name $description".lowercase()
        return when {
            listOf("ssh", "remote", "rdp", "vnc", "rustdesk", "server", "terminal").any(text::contains) -> MarketCategory.REMOTE_ACCESS
            listOf("security", "privacy", "vpn", "firewall", "password", "auth", "encrypt").any(text::contains) -> MarketCategory.SECURITY
            listOf("camera", "music", "audio", "video", "photo", "gallery", "player").any(text::contains) -> MarketCategory.MEDIA
            listOf(" ai ", "llm", "model", "machine learning", "neural", "chatbot").any { text.contains(it) } -> MarketCategory.AI
            else -> MarketCategory.TOOLS
        }
    }

    fun matches(entry: AppEntry, filter: MarketCategory): Boolean = when (filter) {
        MarketCategory.ALL -> true
        MarketCategory.ANDROID -> entry.kind == EntryKind.ANDROID_APP
        MarketCategory.AI -> entry.kind == EntryKind.AI_MODEL || entry.category == MarketCategory.AI
        else -> entry.category == filter
    }
}
