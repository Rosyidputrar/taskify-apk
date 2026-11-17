package com.rosyidputrar.taskify.core.domain.media

import kotlin.math.pow

/** Satu baris lirik bergulir; [t] detik sejak awal lagu. Teks kosong = penanda jeda. */
data class LyricLine(val t: Double, val text: String)

/**
 * Lirik dari `/v1/lyrics/synced` atau `includeLyrics`: [synced] `null` = hanya teks polos,
 * `[]` = instrumental (dipastikan LRCLib) — jangan tampilkan panel lirik.
 */
data class Lyrics(val synced: List<LyricLine>?, val plain: String, val source: String) {
    val instrumental: Boolean get() = synced != null && synced.isEmpty()
    val hasSynced: Boolean get() = !synced.isNullOrEmpty()
    val empty: Boolean get() = !hasSynced && plain.isBlank()
}

object LyricsSync {
    /** Indeks baris aktif untuk posisi [seconds] (baris terakhir yang `t`-nya ≤ posisi), atau -1. */
    fun activeIndex(lines: List<LyricLine>, seconds: Double): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].t <= seconds + 0.15) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/** Satu segmen SponsorBlock; `mute` = bisukan sampai [end], selain itu lompati. */
data class SponsorSegment(val category: String, val start: Double, val end: Double, val mute: Boolean = false)

object SponsorBlock {
    val DEFAULT_CATEGORIES = listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic")

    fun label(category: String): String = when (category) {
        "sponsor" -> "sponsor"
        "intro" -> "intro"
        "outro" -> "outro"
        "interaction" -> "ajakan subscribe"
        "selfpromo" -> "promosi"
        "music_offtopic" -> "bagian non-musik"
        else -> category
    }

    /**
     * Posisi tujuan lompatan bila [seconds] berada di dalam segmen lompat. Segmen bertumpuk:
     * lompat ke `end` terjauh yang aktif. Segmen yang `end`-nya melewati durasi lagu (data basi)
     * dan titik POI (start == end) diabaikan.
     */
    fun skipTarget(segments: List<SponsorSegment>, seconds: Double, durationSeconds: Double?): SponsorSegment? {
        val usable = segments.filter { s ->
            !s.mute && s.end - s.start >= 0.5 && (durationSeconds == null || durationSeconds <= 0 || s.end <= durationSeconds + 1)
        }
        var first: SponsorSegment? = null
        var pos = seconds
        // Segmen bertumpuk/berurutan: lompat ke ujung terjauh, ulangi bila mendarat di segmen lain.
        repeat(8) {
            val hit = usable.filter { pos >= it.start && pos < it.end - 0.3 }.maxByOrNull { it.end } ?: return first?.copy(end = pos)
            if (first == null) first = hit
            pos = hit.end
        }
        return first?.copy(end = pos)
    }

    fun muted(segments: List<SponsorSegment>, seconds: Double): Boolean = segments.any { it.mute && seconds >= it.start && seconds < it.end }
}

object Loudness {
    /** Target kekerasan relatif YouTube (0 dB); lagu yang lebih keras dilirihkan, tidak pernah dikeraskan. */
    fun gain(loudnessDb: Double?): Float {
        if (loudnessDb == null || loudnessDb.isNaN()) return 1f
        return 10.0.pow(-loudnessDb / 20.0).coerceIn(0.1, 1.0).toFloat()
    }
}

/** Kualitas jalur direct (`quality`); konverter mengabaikannya. */
enum class MusicQuality(val value: String, val label: String, val hint: String) {
    Auto("auto", "Otomatis", "Opus terbaik (~170 kbps)"),
    High("high", "Hemat", "Opus tengah (~68 kbps)"),
    Low("low", "Paling hemat", "Bitrate terendah (~32 kbps)");

    companion object {
        fun of(value: String?): MusicQuality = entries.firstOrNull { it.value == value } ?: Auto
    }
}

data class MusicSettings(
    val quality: MusicQuality = MusicQuality.Auto,
    /** Ratakan volume antar lagu dari `loudnessDb`. */
    val normalize: Boolean = true,
    /** Lompati segmen SponsorBlock (hanya bila audio bisa di-seek). */
    val sponsorBlock: Boolean = true,
    /** Selalu lewat konverter MP3 (untuk perangkat yang gagal memutar opus/webm). */
    val forceMp3: Boolean = false,
    /** Arsipkan hasil konverter ke R2 supaya bisa di-seek & diputar ulang tanpa konversi. */
    val archive: Boolean = true,
    /** Memutar satu lagu dari hasil cari/Jelajah → antrean diisi lagu serupa (bukan seluruh daftar hasil). */
    val similarQueue: Boolean = true,
)

/** Pewaktu tidur: berhenti pada [endsAt] (epoch ms) atau di akhir lagu ini. */
data class SleepTimer(val endsAt: Long? = null, val endOfSong: Boolean = false) {
    fun remainingMs(now: Long): Long? = endsAt?.let { (it - now).coerceAtLeast(0) }

    fun label(now: Long): String = when {
        endOfSong -> "Berhenti di akhir lagu"
        endsAt != null -> "Berhenti dalam ${((remainingMs(now) ?: 0) + 59_999) / 60_000} mnt"
        else -> ""
    }

    companion object {
        val OPTIONS = listOf(15, 30, 45, 60)
    }
}
