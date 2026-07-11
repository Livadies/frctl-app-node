package io.frctl.app.data

object RawParsers {
    private val apkRegex = Regex("https?://[^\\s\\\"'<>]+?\\.apk(?=[\\\"'<>\\s]|$)", RegexOption.IGNORE_CASE)
    private val fullNameRegex = Regex("\\\"full_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")

    fun apkLinks(raw: String): List<String> = apkRegex.findAll(raw.replace("\\/", "/"))
        .map { it.value.replace("\\u0026", "&") }
        .distinct().toList()

    fun githubCandidates(raw: String): List<AppEntry> = fullNameRegex.findAll(raw).map { match ->
        val fullName = match.groupValues[1]
        val item = raw.substring(match.range.first, minOf(raw.length, match.range.last + 1200))
        val (owner, name) = fullName.split('/').let { it.first() to it.last() }
        AppEntry(
            id = fullName,
            name = name,
            owner = owner,
            description = value(item, "description") ?: "Open-source Android application",
            repoUrl = value(item, "html_url") ?: "https://github.com/$fullName"
        )
    }.distinctBy { it.id }.toList()

    fun jsonString(raw: String, key: String): String? = value(raw, key)

    private fun value(raw: String, key: String): String? = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        .find(raw)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\/", "/")
}
