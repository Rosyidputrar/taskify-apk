package com.rosyidputrar.taskify.core.domain.sandi

import com.rosyidputrar.taskify.core.common.Limits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser & pembanding berkas konfigurasi (.env / JSON) — port `config-parse.ts`. Teks mentah tetap
 * sumber kebenaran; parser hanya untuk menampilkan nilai per kunci, validasi, dan membandingkan.
 */
enum class ConfigFormat(val value: String) {
    Env("env"), Json("json");

    companion object {
        fun from(v: String?) = if (v == "json") Json else Env
    }
}

data class ConfigEntry(val key: String, val value: String, val line: Int)

enum class ProblemLevel { Error, Warning }

data class ConfigProblem(val level: ProblemLevel, val message: String, val line: Int, val column: Int? = null)

data class ParsedConfig(val entries: List<ConfigEntry>, val problems: List<ConfigProblem>) {
    val blocking: Boolean get() = problems.any { it.level == ProblemLevel.Error }
}

data class ConfigDiff(val added: List<ConfigEntry>, val removed: List<ConfigEntry>, val changed: List<Triple<String, String, String>>, val same: List<ConfigEntry>) {
    val isEmpty get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
}

object ConfigParser {
    const val MAX_BYTES = 256 * 1024

    fun byteLength(text: String) = text.toByteArray(Charsets.UTF_8).size

    fun sizeProblem(text: String): ConfigProblem? {
        val bytes = byteLength(text)
        if (bytes <= MAX_BYTES) return null
        return ConfigProblem(ProblemLevel.Error, "Isi ${Math.round(bytes / 1024.0)} KB melebihi batas ${MAX_BYTES / 1024} KB per versi.", 0)
    }

    private val ENV_LINE = Regex("^(?:export\\s+)?([A-Za-z_][A-Za-z0-9_.]*)\\s*=(.*)$")

    private fun stripInlineComment(raw: String): String {
        val at = Regex("\\s#").find(raw)?.range?.first ?: -1
        return (if (at >= 0) raw.substring(0, at) else raw).trim()
    }

    fun parseEnv(text: String): ParsedConfig {
        val lines = text.split(Regex("\\r?\\n"))
        val entries = mutableListOf<ConfigEntry>()
        val problems = mutableListOf<ConfigProblem>()
        val seen = HashMap<String, Int>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) { i++; continue }
            val m = ENV_LINE.find(trimmed)
            if (m == null) {
                problems += ConfigProblem(ProblemLevel.Warning, "Baris ini bukan `KUNCI=nilai` dan akan diabaikan pembaca .env.", i + 1)
                i++; continue
            }
            val key = m.groupValues[1]
            val rest = m.groupValues[2].trim()
            val value: String
            if (rest.startsWith("\"") || rest.startsWith("'")) {
                val quote = rest[0]
                val closeAt = rest.indexOf(quote, 1)
                if (closeAt >= 0) {
                    value = rest.substring(1, closeAt)
                } else {
                    val parts = mutableListOf(rest.substring(1))
                    var j = i + 1
                    var closed = false
                    while (j < lines.size) {
                        val at = lines[j].indexOf(quote)
                        if (at >= 0) { parts += lines[j].substring(0, at); closed = true; break }
                        parts += lines[j]
                        j++
                    }
                    value = parts.joinToString("\n")
                    if (!closed) {
                        problems += ConfigProblem(ProblemLevel.Error, "Kutip pembuka pada `$key` tidak pernah ditutup.", i + 1)
                        j = lines.size - 1
                    }
                    i = j
                }
            } else {
                value = stripInlineComment(rest)
            }
            seen[key]?.let { before -> problems += ConfigProblem(ProblemLevel.Warning, "`$key` muncul lagi (sudah ada di baris $before). Pembaca .env memakai yang terakhir.", i + 1) }
            seen[key] = i + 1
            entries += ConfigEntry(key, value, i + 1)
            i++
        }
        val last = LinkedHashMap<String, ConfigEntry>()
        entries.forEach { last.remove(it.key); last[it.key] = it }
        // Urutan kemunculan pertama dipertahankan seperti Map JS (set ulang tidak memindah posisi).
        val ordered = LinkedHashMap<String, ConfigEntry>()
        entries.forEach { e -> if (e.key !in ordered) ordered[e.key] = last.getValue(e.key) }
        return ParsedConfig(ordered.values.toList(), problems)
    }

    fun lineColAt(text: String, position: Int): Pair<Int, Int> {
        val upto = text.substring(0, position.coerceIn(0, text.length))
        val lines = upto.split("\n")
        return lines.size to lines.last().length + 1
    }

    private fun flatten(value: JsonElement, prefix: String, out: MutableList<ConfigEntry>) {
        when (value) {
            is JsonObject -> {
                if (value.isEmpty() && prefix.isNotEmpty()) out += ConfigEntry(prefix, "{}", 0)
                value.forEach { (k, v) -> flatten(v, if (prefix.isNotEmpty()) "$prefix.$k" else k, out) }
            }
            is JsonArray -> {
                if (value.isEmpty() && prefix.isNotEmpty()) out += ConfigEntry(prefix, "[]", 0)
                value.forEachIndexed { i, v -> flatten(v, "$prefix.$i", out) }
            }
            is JsonNull -> out += ConfigEntry(prefix, "null", 0)
            is JsonPrimitive -> out += ConfigEntry(prefix, value.content, 0)
        }
    }

    fun parseJson(text: String): ParsedConfig {
        if (text.isBlank()) return ParsedConfig(emptyList(), emptyList())
        val parsed = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            val at = Regex("(?:offset|position) (\\d+)").find(e.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
            val where = at?.let { lineColAt(text, it) }
            return ParsedConfig(
                emptyList(),
                listOf(ConfigProblem(ProblemLevel.Error, where?.let { "JSON tidak valid di baris ${it.first}, kolom ${it.second}." } ?: "JSON tidak valid.", where?.first ?: 0, where?.second)),
            )
        }
        if (parsed !is JsonObject) return ParsedConfig(emptyList(), listOf(ConfigProblem(ProblemLevel.Warning, "JSON ini bukan objek, jadi tidak ada kunci yang bisa dibandingkan.", 1)))
        val out = mutableListOf<ConfigEntry>()
        flatten(parsed, "", out)
        return ParsedConfig(out, emptyList())
    }

    fun parse(text: String, format: ConfigFormat) = if (format == ConfigFormat.Json) parseJson(text) else parseEnv(text)

    /** Usulan format dari nama berkas, lalu dari isinya. */
    fun guessFormat(name: String, text: String = ""): ConfigFormat = when {
        Regex("\\.json$", RegexOption.IGNORE_CASE).containsMatchIn(name.trim()) -> ConfigFormat.Json
        Regex("(^|/|\\.)env(\\.|$)", RegexOption.IGNORE_CASE).containsMatchIn(name.trim()) -> ConfigFormat.Env
        text.trim().startsWith("{") -> ConfigFormat.Json
        else -> ConfigFormat.Env
    }

    /** Bandingkan PER KUNCI (bukan per baris). */
    fun diff(from: List<ConfigEntry>, to: List<ConfigEntry>): ConfigDiff {
        val before = from.associateBy { it.key }
        val after = to.associateBy { it.key }
        val added = mutableListOf<ConfigEntry>()
        val changed = mutableListOf<Triple<String, String, String>>()
        val same = mutableListOf<ConfigEntry>()
        for (e in to) {
            val old = before[e.key]
            when {
                old == null -> added += e
                old.value != e.value -> changed += Triple(e.key, old.value, e.value)
                else -> same += e
            }
        }
        return ConfigDiff(added, from.filter { it.key !in after }, changed, same)
    }

    /** "+2 · 1 berubah · −1"; null bila sama. */
    fun summarize(d: ConfigDiff): String? {
        if (d.isEmpty) return null
        return buildList {
            if (d.added.isNotEmpty()) add("+${d.added.size}")
            if (d.changed.isNotEmpty()) add("${d.changed.size} berubah")
            if (d.removed.isNotEmpty()) add("−${d.removed.size}")
        }.joinToString(" · ")
    }

    /** Huruf kecil & rapi, supaya "Production" dan "production" tidak jadi dua lingkungan. */
    fun normalizeEnvironment(value: String): String? {
        val v = value.trim().lowercase().replace(Regex("\\s+"), "-")
        return if (v.isEmpty()) null else Limits.clip(v, Limits.CONFIG_ENVIRONMENT)
    }
}
