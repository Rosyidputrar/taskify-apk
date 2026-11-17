/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `YouTube.transcript` (v15.1.0, c7f0e24) —
// logika yang sama (cue group pertama tiap grup -> baris LRC
// `[mm:ss.mmm]teks`), tapi ditulis ulang di atas `JsonElement` meniru
// `parse.ts` Worker (bukan `body<GetTranscriptResponse>()`), supaya
// konsisten dengan parser Tahap 2 lainnya. Lihat `licenses/ARCHIVETUNE.md`.

package com.rosyidputrar.taskify.core.innertube.pages

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Transkrip `get_transcript` -> teks LRC (`[mm:ss.mmm]teks` per baris).
 *
 * Jalur: `actions[0].updateEngagementPanelAction.content.transcriptRenderer
 * .body.transcriptBodyRenderer.cueGroups[]`, tiap grup memakai cue PERTAMA
 * (pola ArchiveTune). Teks dibersihkan dari `♪` di ujung dan spasi.
 *
 * @throws NoSuchElementException bila respons tidak memuat cue group sama
 *   sekali (video tanpa transkrip / transkrip tidak tersedia).
 */
fun parseTranscript(root: JsonElement): String {
    val groups = pickArr(
        root,
        "actions.0.updateEngagementPanelAction.content.transcriptRenderer" +
            ".body.transcriptBodyRenderer.cueGroups",
    )
    val lines = groups.mapNotNull { group ->
        val cue = pick(group, "transcriptCueGroupRenderer.cues.0.transcriptCueRenderer")
            ?: return@mapNotNull null
        val startMs = (pick(cue, "startOffsetMs") as? JsonPrimitive)?.longOrNull
            ?: (pick(cue, "startOffsetMs") as? JsonPrimitive)?.intOrNull?.toLong()
            ?: return@mapNotNull null
        val text = pickStr(cue, "cue.simpleText").trim('♪').trim(' ')
        if (text.isEmpty()) return@mapNotNull null
        "[%02d:%02d.%03d]$text".format(startMs / 60000, (startMs / 1000) % 60, startMs % 1000)
    }
    if (lines.isEmpty()) throw NoSuchElementException("transkrip tidak tersedia untuk video ini")
    return lines.joinToString("\n")
}
