package com.rosyidputrar.taskify.core.media

import android.content.Context
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.MusicSettings
import com.rosyidputrar.taskify.core.domain.media.QueueSnapshot
import com.rosyidputrar.taskify.core.domain.media.RadioSource
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Pengaturan musik + antrean tersimpan (lokal per perangkat, dihapus saat logout). URL audio
 * TIDAK disimpan: URL direct berumur ±6 jam dan URL konverter sekali pakai.
 */
class MusicPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)

    var settings: MusicSettings
        get() = MusicSettings(
            quality = MusicQuality.of(prefs.getString("quality", null)),
            normalize = prefs.getBoolean("normalize", true),
            sponsorBlock = prefs.getBoolean("sponsorBlock", true),
            preferMp4a = prefs.getBoolean("preferMp4a", false),
        )
        set(v) {
            // Baca toleran: kunci lama `forceMp3`/`archive` (jalur Worker) diabaikan
            // bila ada, dan dihapus saat pengaturan baru disimpan.
            prefs.edit().putString("quality", v.quality.value).putBoolean("normalize", v.normalize).putBoolean("sponsorBlock", v.sponsorBlock).putBoolean("preferMp4a", v.preferMp4a)
                .remove("forceMp3").remove("archive").apply()
        }

    fun saveQueue(snapshot: QueueSnapshot?) {
        if (snapshot == null) prefs.edit().remove(KEY_QUEUE).apply()
        else prefs.edit().putString(KEY_QUEUE, QueueCodec.encode(snapshot)).apply()
    }

    fun loadQueue(): QueueSnapshot? = prefs.getString(KEY_QUEUE, null)?.let { runCatching { QueueCodec.decode(it) }.getOrNull() }

    /** Logout: lupakan antrean & pengaturan akun ini. */
    fun clear() { prefs.edit().clear().commit() }

    private companion object {
        const val KEY_QUEUE = "queue"
    }
}

/** JSON antrean tersimpan (dibatasi 200 lagu supaya SharedPreferences tetap kecil). */
object QueueCodec {
    private const val MAX_SONGS = 200
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(s: QueueSnapshot): String {
        // Pangkas di sekitar posisi sekarang bila antrean sangat panjang.
        val ordered = s.order.mapNotNull { s.queue.getOrNull(it) }
        val from = (s.position - 20).coerceAtLeast(0).coerceAtMost((ordered.size - MAX_SONGS).coerceAtLeast(0))
        val kept = ordered.drop(from).take(MAX_SONGS)
        val position = (s.position - from).coerceIn(0, (kept.size - 1).coerceAtLeast(0))
        return buildJsonObject {
            put("v", 1)
            put("queue", buildJsonArray { kept.forEach { add(song(it)) } })
            put("position", position)
            put("positionMs", s.positionMs)
            put("repeat", s.repeat.name)
            put("shuffle", s.shuffle)
            put("autoContinue", s.autoContinue)
            s.radio?.let { r -> put("radio", buildJsonObject { put("playlistId", r.playlistId); r.cursor?.let { put("cursor", it) } }) }
        }.toString()
    }

    fun decode(text: String): QueueSnapshot? {
        val o = json.parseToJsonElement(text).jsonObject
        val songs = (o["queue"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::songFrom) }
        if (songs.isEmpty()) return null
        val radio = (o["radio"] as? JsonObject)?.let { r -> r.str("playlistId")?.let { RadioSource(it, r.str("cursor")) } }
        return QueueSnapshot(
            queue = songs,
            // Urutan acak sudah "dibekukan" saat disimpan: dipulihkan berurutan, flag acak tetap.
            order = songs.indices.toList(),
            position = (o["position"] as? JsonPrimitive)?.intOrNull ?: 0,
            positionMs = (o["positionMs"] as? JsonPrimitive)?.longOrNull ?: 0,
            repeat = runCatching { RepeatMode.valueOf(o.str("repeat") ?: "Off") }.getOrDefault(RepeatMode.Off),
            shuffle = (o["shuffle"] as? JsonPrimitive)?.booleanOrNull ?: false,
            autoContinue = (o["autoContinue"] as? JsonPrimitive)?.booleanOrNull ?: true,
            radio = radio,
        )
    }

    private fun song(s: Song) = buildJsonObject {
        put("videoId", s.videoId); put("title", s.title); put("artist", s.artist); put("duration", s.duration)
        s.durationSeconds?.let { put("durationSeconds", it) }
        if (s.live) { put("live", true); put("url", s.url) }
        // Cukup dua ukuran gambar (daftar + sampul).
        val thumbs = listOfNotNull(s.thumbnails.firstOrNull(), s.thumbnails.maxByOrNull { it.width }).distinct()
        put("thumbnails", buildJsonArray { thumbs.forEach { t -> add(buildJsonObject { put("url", t.url); put("width", t.width); put("height", t.height) }) } })
    }

    private fun songFrom(o: JsonObject): Song? {
        val id = o.str("videoId") ?: return null
        val thumbs = (o["thumbnails"] as? JsonArray).orEmpty().mapNotNull { e ->
            val t = e as? JsonObject ?: return@mapNotNull null
            Thumb(t.str("url") ?: return@mapNotNull null, (t["width"] as? JsonPrimitive)?.intOrNull ?: 0, (t["height"] as? JsonPrimitive)?.intOrNull ?: 0)
        }
        val live = (o["live"] as? JsonPrimitive)?.booleanOrNull ?: false
        return Song(id, o.str("title").orEmpty(), o.str("artist").orEmpty(), o.str("duration").orEmpty(), thumbs,
            if (live) o.str("url").orEmpty() else "", (o["durationSeconds"] as? JsonPrimitive)?.intOrNull, live)
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
