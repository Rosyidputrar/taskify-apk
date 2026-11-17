package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * Fixture JSON minimal untuk jalur YouTube di tes lirik — bentuknya
 * mengikuti jalur yang dibaca `parseNext` / `parseLyrics` /
 * `parseTranscript` (`:core:innertube`), bukan respons asli lengkap.
 */
internal object YouTubeFixtures {
    const val VIDEO_ID = "dQw4w9WgXcQ"
    const val LYRICS_ID = "MPLYt_abc123"
    const val LYRICS_TEXT = "Look at the stars\nLook how they shine for you"

    /** `next`: tab pertama berisi lagu + endpoint lirik `MPLYt_…`. */
    const val NEXT_JSON = """{"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[
      {"tabRenderer":{"endpoint":{"browseEndpoint":{"browseId":"MPLYt_abc123"}},"content":{"musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[{"playlistPanelVideoRenderer":{"videoId":"dQw4w9WgXcQ","title":{"runs":[{"text":"Yellow"}]},"longBylineText":{"runs":[{"text":"Coldplay"}]},"lengthText":{"runs":[{"text":"4:26"}]},"thumbnail":{"thumbnails":[]}}}]}}}}}},
      {"tabRenderer":{}}
    ]}}}}}"""

    /** `browse` MPLYt: rak deskripsi berisi teks lirik. */
    const val BROWSE_JSON = """{"contents":{"sectionListRenderer":{"contents":[{"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"Look at the stars\nLook how they shine for you"}]}}}]}}}"""

    /** `get_transcript`: dua cue group -> dua baris LRC. */
    const val TRANSCRIPT_JSON = """{"actions":[{"updateEngagementPanelAction":{"content":{"transcriptRenderer":{"body":{"transcriptBodyRenderer":{"cueGroups":[
      {"transcriptCueGroupRenderer":{"cues":[{"transcriptCueRenderer":{"cue":{"simpleText":"Look at the stars"},"startOffsetMs":0,"durationMs":3000}}]}},
      {"transcriptCueGroupRenderer":{"cues":[{"transcriptCueRenderer":{"cue":{"simpleText":"♪ Look how they shine"},"startOffsetMs":5000,"durationMs":3000}}]}}
    ]}}}}}}]}"""
}

/** Fasad `YouTube` dengan transport MockEngine (pola `YouTubeFacadeTest`). */
internal fun youtubeWith(handler: suspend (String) -> Pair<String, HttpStatusCode>): YouTube {
    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val (body, status) = handler(path)
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }
    return YouTube(InnerTube(HttpClient(engine) { expectSuccess = true }))
}
