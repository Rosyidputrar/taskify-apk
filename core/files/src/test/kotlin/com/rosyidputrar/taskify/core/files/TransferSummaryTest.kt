package com.rosyidputrar.taskify.core.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransferSummaryTest {
    private fun up(key: String, phase: UploadPhase, progress: Float = 0f, size: Long = 100) = UploadJob(key, "$key.zip", size, progress, phase)

    @Test fun noneActive() {
        assertNull(summarizeTransfers(emptyList(), emptyList()))
        // yang sudah selesai/gagal/batal tidak menahan layanan
        assertNull(summarizeTransfers(listOf(up("a", UploadPhase.Done), up("b", UploadPhase.Failed), up("c", UploadPhase.Cancelled)), emptyList()))
    }

    @Test fun singleUpload() {
        val s = summarizeTransfers(listOf(up("a", UploadPhase.Uploading, 0.42f)), emptyList())!!
        assertEquals("Mengunggah a.zip", s.title)
        assertEquals("Mengunggah 42%", s.text)
        assertEquals(0.42f, s.progress!!, 0.001f)
        assertEquals(1, s.count)
        // memesan tempat = progres tak tentu
        assertNull(summarizeTransfers(listOf(up("a", UploadPhase.Reserving)), emptyList())!!.progress)
    }

    @Test fun weightedBySize() {
        val s = summarizeTransfers(listOf(up("a", UploadPhase.Uploading, 1f, size = 300), up("b", UploadPhase.Uploading, 0f, size = 100)), emptyList())!!
        assertEquals("Mengunggah 2 berkas", s.title)
        assertEquals(0.75f, s.progress!!, 0.001f)
    }

    @Test fun downloadsAndMixed() {
        assertEquals("Mengunduh laporan.pdf", summarizeTransfers(emptyList(), listOf(DownloadJob("d", "laporan.pdf", 0.5f)))!!.title)
        assertNull(summarizeTransfers(emptyList(), listOf(DownloadJob("d", "x", null)))!!.progress)
        val mixed = summarizeTransfers(listOf(up("a", UploadPhase.Uploading, 0.5f)), listOf(DownloadJob("d", "x", 0.5f)))!!
        assertEquals("2 transfer berjalan", mixed.title)
        assertEquals(2, mixed.count)
    }
}
