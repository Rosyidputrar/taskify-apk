package com.rosyidputrar.taskify.core.innertube.models.body

import com.rosyidputrar.taskify.core.innertube.models.Context
import kotlinx.serialization.Serializable

/**
 * Badan permintaan InnerTube — ditulis dari bentuk badan yang dikirim Worker
 * (`worker/innertube.ts`: `searchCall`, `browseCall`, `nextCall`,
 * `radioCall`, `playerCall`, `suggestCall`). Nama field adalah fakta API
 * InnerTube, bukan ekspresi ArchiveTune; struktur `NextBody` menggabungkan
 * kedua sumber (Worker: `tunerSettingValue`, ArchiveTune: `playlistSetVideoId`
 * / `index` / `params` / `continuation`).
 */
@Serializable
data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null,
    val continuation: String? = null,
)

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
    val formData: FormData? = null,
) {
    /** `formData.selectedValues`, mis. `["ID"]` untuk tangga lagu Indonesia. */
    @Serializable
    data class FormData(val selectedValues: List<String>)
}

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val index: Int? = null,
    val params: String? = null,
    val continuation: String? = null,
    val isAudioOnly: Boolean = true,
    val enablePersistentPlaylistPanel: Boolean = true,
    val tunerSettingValue: String? = "AUTOMIX_SETTING_NORMAL",
)

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val racyCheckOk: Boolean = true,
    val contentCheckOk: Boolean = true,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int? = null,
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)

/**
 * Badan `get_transcript` — diadaptasi dari ArchiveTune
 * `innertube/models/body/GetTranscriptBody.kt` (GPL-3.0; lihat
 * `licenses/ARCHIVETUNE.md`). `params` = base64 dari `"\n\x0b<videoId>"`.
 */
@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
