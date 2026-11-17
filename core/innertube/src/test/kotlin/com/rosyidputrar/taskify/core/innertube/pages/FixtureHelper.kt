package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.innertube.InnertubeJson
import kotlinx.serialization.json.JsonElement
import java.util.zip.GZIPInputStream

/**
 * Pembaca fixture respons InnerTube (`.json.gz` di `src/test/resources/fixtures/`),
 * pola yang sama dengan `parse.test.ts` Worker. Fixture sudah dibersihkan
 * dari `visitorData` asli di Tahap 0.
 */
internal object FixtureHelper {
    fun json(name: String): JsonElement = InnertubeJson.parseToJsonElement(text(name))

    fun text(name: String): String {
        val stream = FixtureHelper::class.java.classLoader
            ?.getResourceAsStream("fixtures/$name.json.gz")
            ?: error("fixture tidak ditemukan: $name")
        return GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).readText()
    }
}
