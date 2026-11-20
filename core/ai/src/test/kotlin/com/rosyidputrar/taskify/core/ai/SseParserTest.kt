package com.rosyidputrar.taskify.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {
    @Test fun eventsSplitAcrossChunksCrlfCommentsAndMultilineData() {
        val p = SseParser()
        val out = mutableListOf<SseEvent>()
        listOf(": komentar\r\n", "event: content_block_delta\r\nda", "ta: {\"a\":1}\r", "\n\r\n", "data: baris1\ndata: baris2\n", "\n", "data:tanpaspasi\n\n")
            .forEach { out += p.feed(it) }
        out += p.finish()
        assertEquals(
            listOf(SseEvent("content_block_delta", "{\"a\":1}"), SseEvent(null, "baris1\nbaris2"), SseEvent(null, "tanpaspasi")),
            out,
        )
    }

    @Test fun lastEventWithoutBlankLineIsFlushedOnFinish() {
        val p = SseParser()
        assertEquals(emptyList(), p.feed("data: [DONE]"))
        assertEquals(listOf(SseEvent(null, "[DONE]")), p.finish())
    }

    @Test fun blankLinesWithoutDataEmitNothing() {
        val p = SseParser()
        assertEquals(emptyList(), p.feed("\n\nevent: ping\n\n"))
    }
}
