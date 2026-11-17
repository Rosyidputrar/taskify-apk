package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import com.rosyidputrar.taskify.core.domain.media.Track
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser respons `player` untuk metadata + loudness — port
 * `loudnessFromPlayer` dan `fetchPlayerTrack` dari `worker/direct.ts`.
 * Dipakai `YouTube.track()` sebagai pelengkap `next` (loudness) dan sebagai
 * fallback bila `next` diblokir. Pemilihan format audio ada di
 * `DirectStreamResolver` (Tahap 3), bukan di sini.
 */

/** Kekerasan satu lagu dari respons `player`. */
data class PlayerLoudness(
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
)

private fun numOrNull(v: JsonElement?): Double? {
    val n = (v as? JsonPrimitive)?.content?.toDoubleOrNull()
    return if (n != null && n.isFinite()) n else null
}

/**
 * Kekerasan dari satu respons `player` mentah. Sumber utama
 * `playerConfig.audioConfig` (`loudnessDb` + `perceptualLoudnessDb`); bila
 * `audioConfig` tidak memberi `loudnessDb`, jatuh ke `loudnessDb` format
 * audio pertama yang memilikinya. `null` = respons ini tidak memuat
 * kekerasan sama sekali.
 */
fun loudnessFromPlayer(root: JsonElement?): PlayerLoudness? {
    val audioConfig = pick(root, "playerConfig.audioConfig")
    var loudness = numOrNull(pick(audioConfig, "loudnessDb"))
    if (loudness == null) {
        val formats = pickArr(root, "streamingData.formats") + pickArr(root, "streamingData.adaptiveFormats")
        for (f in formats) {
            loudness = numOrNull(pick(f, "loudnessDb"))
            if (loudness != null) break
        }
    }
    val perceptual = numOrNull(pick(audioConfig, "perceptualLoudnessDb"))
    if (loudness == null && perceptual == null) return null
    return PlayerLoudness(loudness, perceptual)
}

/**
 * Metadata + kekerasan satu lagu dari endpoint `player`.
 * `null` bila `playabilityStatus` bukan OK (mis. LOGIN_REQUIRED/UNPLAYABLE)
 * atau responsnya bukan JSON objek. `song` null bila respons tidak memuat
 * `videoDetails` — `loudnessDb` tetap diisi bila ada.
 */
fun parsePlayerTrack(root: JsonElement?, videoId: String): Track? {
    if (root !is JsonObject) return null
    val status = pickStr(root, "playabilityStatus.status")
    if (status != "OK") return null
    val loudness = loudnessFromPlayer(root)
    val vd = pick(root, "videoDetails")
    if (!isObj(vd)) {
        return Track(
            videoId = videoId,
            lyricsId = "",
            upNext = emptyList(),
            durationSeconds = null,
            loudnessDb = loudness?.loudnessDb,
            lyrics = null,
            lyricsChecked = false,
            song = null,
        )
    }
    val seconds = (pick(vd, "lengthSeconds") as? JsonPrimitive)?.content?.toIntOrNull()
    val thumbList = thumbs(pickArr(vd, "thumbnail.thumbnails"))
    val song = Song(
        videoId = videoId,
        title = pickStr(vd, "title"),
        artist = pickStr(vd, "author"),
        duration = if (seconds != null) formatDuration(seconds) else "",
        thumbnails = thumbList.ifEmpty {
            listOf(Thumb(url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg", width = 480, height = 360))
        },
        url = watchUrl(videoId),
        durationSeconds = seconds,
    )
    return Track(
        videoId = videoId,
        lyricsId = "",
        upNext = emptyList(),
        durationSeconds = seconds,
        loudnessDb = loudness?.loudnessDb,
        lyrics = null,
        lyricsChecked = false,
        song = song,
    )
}
