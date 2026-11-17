package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.Thumb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper penelusuran JSON — port fungsi-fungsi kecil `src/ytmusic/parse.ts`
 * (Worker). Ditulis baru dari perilaku itu; bentuk JSON InnerTube tidak
 * didokumentasikan, jadi semua akses defensif (hilang = null/kosong,
 * bukan exception).
 */

/** Ambil nilai lewat jalur "a.0.b" — padanan `pick` di `parse.ts`. */
fun pick(obj: JsonElement?, path: String): JsonElement? {
    var cur: JsonElement? = obj
    for (part in path.split('.')) {
        if (cur == null || cur is JsonNull) return null
        cur = when (cur) {
            is JsonArray -> part.toIntOrNull()?.let { cur.getOrNull(it) }
            is JsonObject -> cur[part]
            else -> return null
        }
    }
    return cur?.takeUnless { it is JsonNull }
}

fun pickStr(obj: JsonElement?, path: String): String {
    val v = pick(obj, path)
    return if (v is JsonPrimitive && v.isString) v.content else ""
}

fun pickArr(obj: JsonElement?, path: String): List<JsonElement> {
    val v = pick(obj, path)
    return if (v is JsonArray) v.toList() else emptyList()
}

fun isObj(v: JsonElement?): Boolean = v is JsonObject

/**
 * Mencari nilai pertama pada kunci bernama [key], sedalam apa pun.
 * Dipakai untuk token lanjutan karena posisinya berpindah-pindah.
 */
fun deepFind(node: JsonElement?, key: String): JsonElement? {
    if (node == null || node is JsonNull) return null
    if (node is JsonArray) {
        for (v in node) {
            val got = deepFind(v, key)
            if (got != null) return got
        }
        return null
    }
    if (node is JsonObject) {
        if (key in node && node[key] !is JsonNull) return node[key]
        for (v in node.values) {
            val got = deepFind(v, key)
            if (got != null) return got
        }
    }
    return null
}

/** Teks dari `runs`, membuang yang kosong dan pemisah "•". */
fun runTexts(runs: List<JsonElement>): List<String> {
    val out = mutableListOf<String>()
    for (r in runs) {
        if (r !is JsonObject) continue
        val t = (r["text"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (t.isNotEmpty() && t != "•") out.add(t)
    }
    return out
}

/** Teks dari `runs` — potongan tebal dan biasa disambung apa adanya. */
fun runsText(runs: List<JsonElement>): String =
    runs.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
        .joinToString("") { it.content.orEmpty() }
        .trim()

fun thumbs(raw: List<JsonElement>): List<Thumb> {
    val out = mutableListOf<Thumb>()
    for (t in raw) {
        if (t !is JsonObject) continue
        val url = (t["url"] as? JsonPrimitive)?.content ?: continue
        out.add(
            Thumb(
                url = url,
                width = (t["width"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                height = (t["height"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
            ),
        )
    }
    return out
}

fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"
fun browseUrl(browseId: String) = "https://music.youtube.com/browse/$browseId"
fun playlistUrl(playlistId: String) = "https://music.youtube.com/playlist?list=$playlistId"

private val DURATION = Regex("""^\d+[:.]\d{2}$""")
private val YEAR = Regex("""^\d{4}$""")

fun isDurationText(text: String): Boolean = DURATION.matches(text)
fun isYearText(text: String): Boolean = YEAR.matches(text)

/**
 * `"3.35"` (lokal id) atau `"3:35"` atau `"1:02:03"` → detik. Angka polos
 * (`"215"`) dibaca sebagai detik. `null` bila bentuknya tidak dikenali —
 * pemanggil harus memperlakukannya sebagai "durasi tidak diketahui".
 * Aturan tolak sama seperti `parseDurationText` di `parse.ts`: negatif,
 * detik/menit ≥ 60, pemisah campuran, spasi di mana pun dibuang dulu.
 */
fun parseDurationText(text: String): Int? {
    val s = text.replace(Regex("\\s+"), "")
    if (s.isEmpty()) return null
    if (Regex("""^\d+$""").matches(s)) return s.toIntOrNull()
    val m = Regex("""^(\d+)([.:])(\d{1,2})(?:\2(\d{1,2}))?$""").find(s) ?: return null
    val a = m.groupValues[1].toIntOrNull() ?: return null
    val b = m.groupValues[3].toIntOrNull() ?: return null
    val c = m.groupValues[4]
    if (c.isNotEmpty()) {
        val cc = c.toIntOrNull() ?: return null
        if (b >= 60 || cc >= 60) return null
        return a * 3600 + b * 60 + cc
    }
    if (b >= 60) return null
    return a * 60 + b
}

/**
 * Durasi episode podcast: "42 mnt", "1 j 5 mnt", "1 j", "30 dtk" (`hl=id`).
 * Bila hulu menulis "m:ss" (mis. `hl=en`), jatuh ke [parseDurationText].
 */
fun parseEpisodeDurationText(text: String): Int? {
    val s = text.replace(Regex("\\s+"), " ").trim().lowercase()
    if (s.isEmpty()) return null
    val m = Regex("""^(?:(\d+)\s*j(?:am)?\s*)?(?:(\d+)\s*mnt\s*)?(?:(\d+)\s*dtk\s*)?$""").find(s)
    if (m != null) {
        val h = m.groupValues[1]
        val mn = m.groupValues[2]
        val dt = m.groupValues[3]
        if (h.isEmpty() && mn.isEmpty() && dt.isEmpty()) return null
        return (h.toIntOrNull() ?: 0) * 3600 + (mn.toIntOrNull() ?: 0) * 60 + (dt.toIntOrNull() ?: 0)
    }
    return parseDurationText(text)
}

/** Detik jadi teks durasi "m:ss" (atau "h:mm:ss" bila lebih dari satu jam). */
fun formatDuration(totalSeconds: Int): String {
    val s = maxOf(0, totalSeconds)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    else "$m:${sec.toString().padStart(2, '0')}"
}

/** Label jenis item ikut bahasa `hl` yang dikirim, jadi dua bahasa dikenali. */
val SONG_WORDS = setOf("lagu", "song", "video", "videos")
val ALBUM_WORDS = setOf("album", "ep", "single")
val ARTIST_WORDS = setOf("artis", "artist")
val PLAYLIST_WORDS = setOf("playlist", "daftar putar")
val EPISODE_WORDS = setOf("episode")
