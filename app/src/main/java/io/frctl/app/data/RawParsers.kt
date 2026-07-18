package io.frctl.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RawParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Regex = Regex("(?i)(?<![a-f0-9])[a-f0-9]{64}(?![a-f0-9])")

    fun apkLinks(raw: String): List<String> = releaseAssets(raw)
        .filter { it.endsWith(".apk", ignoreCase = true) }

    fun sha256Links(raw: String): List<String> = releaseAssets(raw)
        .filter { it.endsWith(".sha256", ignoreCase = true) }

    fun sha256(raw: String): String? = sha256Regex.find(raw)?.value?.lowercase()

    fun githubCandidates(raw: String): List<AppEntry> = runCatching {
        rootObject(raw).array("items").mapNotNull { element ->
            val item = element.jsonObject
            val fullName = item.string("full_name")?.takeIf { it.contains('/') } ?: return@mapNotNull null
            val (owner, name) = fullName.split('/', limit = 2)
            val description = item.string("description") ?: "Open-source Android application"
            AppEntry(
                id = fullName,
                name = name,
                owner = owner,
                description = description,
                repoUrl = "https://github.com/$fullName",
                iconUrl = "https://raw.githubusercontent.com/$fullName/HEAD/fastlane/metadata/android/en-US/images/icon.png",
                fallbackIconUrl = item.objectOrNull("owner")?.string("avatar_url"),
                stars = item.int("stargazers_count") ?: 0,
                updatedAt = item.string("updated_at").orEmpty(),
                category = MarketplaceClassifier.android(name, description)
            )
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun huggingFaceModels(raw: String): List<AppEntry> = runCatching {
        rootArray(raw).mapNotNull { element ->
            val item = element.jsonObject
            val id = listOfNotNull(item.string("id"), item.string("modelId"))
                .firstOrNull { it.contains('/') } ?: return@mapNotNull null
            val (owner, name) = id.split('/', limit = 2)
            val pipeline = item.string("pipeline_tag") ?: "model"
            AppEntry(
                id = id,
                name = name,
                owner = owner,
                description = "Hugging Face · ${pipeline.replace('-', ' ')}",
                repoUrl = "https://huggingface.co/$id",
                source = "Hugging Face",
                stars = item.int("likes") ?: 0,
                downloads = item.int("downloads") ?: 0,
                updatedAt = item.string("lastModified").orEmpty(),
                kind = EntryKind.AI_MODEL,
                category = MarketCategory.AI,
                pipelineTag = pipeline
            )
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun jsonString(raw: String, key: String): String? = runCatching { rootObject(raw).string(key) }.getOrNull()

    fun jsonInt(raw: String, key: String): Int? = runCatching { rootObject(raw).int(key) }.getOrNull()

    private fun releaseAssets(raw: String): List<String> = runCatching {
        rootObject(raw).array("assets")
            .mapNotNull { it.jsonObject.string("browser_download_url") }
            .filter { it.startsWith("https://") }
            .distinct()
    }.getOrDefault(emptyList())

    private fun rootObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
    private fun rootArray(raw: String): JsonArray = json.parseToJsonElement(raw).jsonArray
    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.array(key: String): JsonArray = this[key]?.let { runCatching { it.jsonArray }.getOrNull() } ?: JsonArray(emptyList())
    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key]?.let { runCatching { it.jsonObject }.getOrNull() }
}
