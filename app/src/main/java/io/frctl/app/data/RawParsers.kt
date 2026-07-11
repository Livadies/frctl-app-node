package io.frctl.app.data

object RawParsers {
    private val apkRegex = Regex("https?://[^\\s\\\"'<>]+?\\.apk(?=[\\\"'<>\\s]|$)", RegexOption.IGNORE_CASE)
    private val fullNameRegex = Regex("\\\"full_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")

    fun apkLinks(raw: String): List<String> = apkRegex.findAll(raw.replace("\\/", "/"))
        .map { it.value.replace("\\u0026", "&") }
        .distinct().toList()

    fun githubCandidates(raw: String): List<AppEntry> {
        val matches = fullNameRegex.findAll(raw).toList()
        return matches.mapIndexed { index, match ->
        val fullName = match.groupValues[1]
        val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
        val item = raw.substring(match.range.first, end)
        val (owner, name) = fullName.split('/').let { it.first() to it.last() }
        AppEntry(
            id = fullName,
            name = name,
            owner = owner,
            description = value(item, "description") ?: "Open-source Android application",
            repoUrl = "https://github.com/$fullName",
            iconUrl = value(item, "avatar_url"),
            stars = number(item, "stargazers_count"),
            updatedAt = value(item, "updated_at") ?: ""
        )
        }.distinctBy { it.id }
    }

    fun jsonString(raw: String, key: String): String? = value(raw, key)

    private fun value(raw: String, key: String): String? = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        .find(raw)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\/", "/")

    private fun number(raw: String, key: String): Int = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\d+)")
        .find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
