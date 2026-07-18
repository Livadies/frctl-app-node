package io.frctl.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelsTest {
    @Test
    fun parsesOnlySafeRunnableModelEntries() {
        val raw = """
            {"models":[
              {"id":"org/model","displayName":"Small model","fileName":"model.task","sizeBytes":42,"sha256":"${"a".repeat(64)}","minRamBytes":100,"license":"Apache-2.0"},
              {"id":"org/model","displayName":"Unsafe","fileName":"../model.task","sizeBytes":42,"sha256":"${"b".repeat(64)}","minRamBytes":100,"license":"MIT"}
            ]}
        """.trimIndent()

        val models = parseRunnableModels(raw)

        assertEquals(1, models.size)
        assertEquals("org/model::model.task", models.single().key)
        assertTrue(models.single().downloadUrl.startsWith("https://huggingface.co/"))
    }

    @Test
    fun computesLowercaseSha256() {
        val file = File.createTempFile("frctl-model", ".task")
        try {
            file.writeText("FRCTL")
            assertEquals("03e31f2c5d1f6376215766cf6cef9ecdfd17a885d5ecf5fd188046d39f89f7f6", fileSha256(file))
        } finally {
            file.delete()
        }
    }
}
