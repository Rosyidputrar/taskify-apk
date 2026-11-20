/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `sponsorblock/SponsorBlockModels.kt`
// (v15.1.0, c7f0e24) — enum kategori, settings, model respons, dan
// `normalizeSponsorBlockApiUrl` dipertahankan. Yang DIUBAH untuk Taskify
// (lihat `licenses/ARCHIVETUNE.md`):
//   - Daftar kategori memakai 12 slug yang DIBUKTIKAN Worker
//     (`worker/sponsorblock.ts`: 11 dari wiki Types SponsorBlock +
//     `music_offtopic` lewat probe) — ArchiveTune hanya punya 9.
//   - Segmen mentah dipetakan ke model domain Taskify
//     (`SponsorSegment` di `:core:domain`) lewat [toDomainSegments],
//     bukan ke `SponsorBlockRawSegment` + controller ArchiveTune.
//   - Tanpa DataStore: pengaturan tinggal di `MusicSettings` + parameter
//     repository (Tahap 6-7), bukan di sini.

package com.rosyidputrar.taskify.core.media.sponsorblock

import com.rosyidputrar.taskify.core.domain.media.SponsorSegment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

const val DEFAULT_SPONSORBLOCK_API_URL = "https://sponsor.ajay.app"

enum class SponsorBlockCategory(
    val apiValue: String,
) {
    MUSIC_OFF_TOPIC("music_offtopic"),
    SPONSOR("sponsor"),
    INTRO("intro"),
    OUTRO("outro"),
    SELF_PROMOTION("selfpromo"),
    PREVIEW("preview"),
    FILLER("filler"),
    INTERACTION("interaction"),
    HOOK("hook"),
    POI_HIGHLIGHT("poi_highlight"),
    EXCLUSIVE_ACCESS("exclusive_access"),
    CHAPTER("chapter"),
    ;

    companion object {
        fun fromApiValue(value: String): SponsorBlockCategory? =
            entries.firstOrNull { it.apiValue == value }
    }
}

data class SponsorBlockSettings(
    val enabled: Boolean,
    val categories: Set<SponsorBlockCategory>,
    val apiUrl: String,
) {
    companion object {
        val Default =
            SponsorBlockSettings(
                enabled = true,
                categories = SponsorBlockCategory.entries.toSet(),
                apiUrl = DEFAULT_SPONSORBLOCK_API_URL,
            )
    }
}

@Serializable
internal data class SponsorBlockVideoResponse(
    @SerialName("videoID") val videoId: String,
    val segments: List<SponsorBlockSegmentResponse> = emptyList(),
)

@Serializable
internal data class SponsorBlockSegmentResponse(
    val segment: List<Double> = emptyList(),
    val category: String = "",
    val actionType: String = "",
    val UUID: String? = null,
    val votes: Int? = null,
    val description: String? = null,
    val videoDuration: Double? = null,
)

/**
 * Satu item mentah hulu -> [SponsorSegment] domain, atau `null` bila
 * itemnya rusak (aturan Worker `normalizeSegment`):
 * kategori di luar daftar yang diminta, rentang bukan `[start, end]`
 * angka, `start > end`, atau waktu negatif dibuang. `start == end`
 * DITERIMA — itu titik POI (`poi_highlight`).
 */
internal fun List<SponsorBlockSegmentResponse>.toDomainSegments(): List<SponsorSegment> =
    mapNotNull { raw ->
        if (SponsorBlockCategory.fromApiValue(raw.category) == null) return@mapNotNull null
        if (raw.segment.size != 2) return@mapNotNull null
        val start = raw.segment[0]
        val end = raw.segment[1]
        if (start < 0 || end < 0 || start > end) return@mapNotNull null
        SponsorSegment(raw.category, start, end, raw.actionType == "mute")
    }.sortedBy { it.start }

internal fun normalizeSponsorBlockApiUrl(value: String): String? {
    val candidate = value.trim().ifEmpty { DEFAULT_SPONSORBLOCK_API_URL }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    return candidate.trimEnd('/')
}
