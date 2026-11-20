package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.media.RAW_DETAIL_MAX_CHARS
import com.rosyidputrar.taskify.core.domain.media.buildRawDetail
import com.rosyidputrar.taskify.core.domain.media.causeChainText
import com.rosyidputrar.taskify.core.domain.media.truncateRawDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawErrorDetailTest {

    @Test fun buildRawDetailJoinsLabelValueLines() {
        val detail = buildRawDetail(
            "videoId" to "abc123",
            "errorCodeName" to "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            "errorCode" to "1002001",
        )
        assertEquals(
            "videoId: abc123\nerrorCodeName: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED\nerrorCode: 1002001",
            detail,
        )
    }

    @Test fun buildRawDetailSkipsBlankValues() {
        val detail = buildRawDetail("videoId" to "abc123", "message" to null, "cause" to "   ", "host" to "googlevideo.com")
        assertEquals("videoId: abc123\nhost: googlevideo.com", detail)
    }

    @Test fun buildRawDetailTruncatesEachLineToLimit() {
        val long = "x".repeat(RAW_DETAIL_MAX_CHARS + 100)
        val detail = buildRawDetail("message" to long)
        val line = detail.removePrefix("message: ")
        assertTrue(line.length <= RAW_DETAIL_MAX_CHARS + "… (dipotong)".length)
        assertTrue(line.endsWith("… (dipotong)"))
    }

    @Test fun buildRawDetailKeepsMultiLineValuesLineByLine() {
        val chain = "IOException: gagal\nHttpException: 403\n" + "y".repeat(RAW_DETAIL_MAX_CHARS + 10)
        val detail = buildRawDetail("cause" to chain)
        val lines = detail.removePrefix("cause: ").split('\n')
        assertEquals(3, lines.size)
        assertTrue(lines[0] == "IOException: gagal")
        assertTrue(lines[2].endsWith("… (dipotong)"))
    }

    @Test fun truncateRawDetailKeepsShortValuesUntouched() {
        assertEquals("abc", truncateRawDetail("  abc  "))
        assertEquals("", truncateRawDetail(null))
        assertEquals("", truncateRawDetail("   "))
    }

    @Test fun causeChainTextFlattensNestedCauses() {
        val root = IllegalStateException("akar")
        val mid = java.io.IOException("tengah", root)
        val top = RuntimeException("atas", mid)
        assertEquals("RuntimeException: atas\nIOException: tengah\nIllegalStateException: akar", causeChainText(top))
    }

    @Test fun causeChainTextTruncatesEachCauseMessage() {
        val long = "z".repeat(RAW_DETAIL_MAX_CHARS + 50)
        val text = causeChainText(RuntimeException(long))
        assertTrue(text!!.endsWith("… (dipotong)"))
        assertFalse(text.contains("z".repeat(RAW_DETAIL_MAX_CHARS + 50)))
    }

    @Test fun causeChainTextLimitsDepth() {
        var t: Throwable = RuntimeException("level 0")
        repeat(9) { i -> t = RuntimeException("level ${i + 1}", t) }
        val lines = causeChainText(t)!!.split('\n')
        assertEquals(5, lines.size)
        assertTrue(lines.first().contains("level 9"))
        assertTrue(lines.last().contains("level 5"))
    }

    @Test fun causeChainTextNullWithoutCause() {
        assertNull(causeChainText(null))
        // Tanpa pesan, yang tampil hanya nama kelasnya.
        assertEquals("RuntimeException", causeChainText(RuntimeException()))
    }
}
