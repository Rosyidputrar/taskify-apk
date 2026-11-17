package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.Suggestion
import kotlinx.serialization.json.JsonElement

/**
 * Parser saran pencarian — port `parseSuggestions` dari
 * `src/ytmusic/parse.ts` (Worker). Respons berisi dua seksi:
 * `searchSuggestionRenderer` (saran query) dan
 * `musicResponsiveListItemRenderer` (entitas). Query yang tidak dikenal
 * dijawab 200 TANPA `contents` — jadi respons kosong adalah daftar kosong,
 * bukan error.
 */

/** Batas pengaman: hulu biasanya mengirim ~10 saran. */
private const val MAX_SUGGESTIONS = 25

private val ID11 = Regex("""^[a-zA-Z0-9_-]{11}$""")
private val CHANNEL_ID = Regex("""^UC[a-zA-Z0-9_-]{22}$""")

fun parseSuggestions(res: JsonElement): List<Suggestion> {
    val out = mutableListOf<Suggestion>()
    for (section in pickArr(res, "contents")) {
        for (item in pickArr(section, "searchSuggestionsSectionRenderer.contents")) {
            val query = pick(item, "searchSuggestionRenderer")
            if (isObj(query)) {
                val text = runsText(pickArr(query, "suggestion.runs"))
                if (text.isNotEmpty()) out.add(Suggestion(text, "query"))
                continue
            }
            val entity = pick(item, "musicResponsiveListItemRenderer")
            if (!isObj(entity)) continue
            val cols = pickArr(entity, "flexColumns")
            val text = runsText(
                pickArr(cols.getOrNull(0), "musicResponsiveListItemFlexColumnRenderer.text.runs"),
            )
            if (text.isEmpty()) continue
            val subtitle = runsText(
                pickArr(cols.getOrNull(1), "musicResponsiveListItemFlexColumnRenderer.text.runs"),
            )
            val nav = pick(entity, "navigationEndpoint")
            val videoId = pickStr(nav, "watchEndpoint.videoId")
            if (videoId.isNotEmpty() && ID11.matches(videoId)) {
                out.add(Suggestion(text, "video", subtitle, videoId = videoId))
                continue
            }
            val browseId = pickStr(nav, "browseEndpoint.browseId")
            if (browseId.isNotEmpty() && CHANNEL_ID.matches(browseId)) {
                out.add(Suggestion(text, "artist", subtitle, browseId = browseId))
                continue
            }
            // Entitas tanpa endpoint yang dikenali: dilewati, bukan ditebak.
        }
        if (out.size >= MAX_SUGGESTIONS) break
    }
    return out.take(MAX_SUGGESTIONS)
}
