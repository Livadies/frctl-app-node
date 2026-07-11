package io.frctl.app.data

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
    val updatedAt: String = ""
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
    val cached: Boolean = false
)
