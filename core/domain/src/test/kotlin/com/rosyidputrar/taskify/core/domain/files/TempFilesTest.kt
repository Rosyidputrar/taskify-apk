package com.rosyidputrar.taskify.core.domain.files

import com.rosyidputrar.taskify.core.domain.files.TempFiles.countsTowardQuota
import com.rosyidputrar.taskify.core.domain.files.TempFiles.expiryLabel
import com.rosyidputrar.taskify.core.domain.files.TempFiles.expiryShort
import com.rosyidputrar.taskify.core.domain.files.TempFiles.quotaProblem
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kasus disalin dari tempfile-utils.test.ts webapp. */
class TempFilesTest {
    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()
    private val mb = 1024L * 1024

    private fun row(
        id: String = "f1", size: Long = 10 * mb, status: TempFileStatus = TempFileStatus.Ready, isPublic: Boolean = true,
        expiresAt: String? = null, createdAt: String = "2026-11-01 11:00:00Z", updatedAt: String = "2026-11-01 11:00:00Z",
        deletedAt: String? = null, name: String = "a.pdf",
    ) = TempFile(id, name, "application/pdf", size, status, "x".repeat(43), isPublic, expiresAt, 0, null, createdAt, updatedAt, deletedAt)

    private fun at(ms: Long) = Instant.ofEpochMilli(now + ms).toString()

    @Test fun quota() {
        assertTrue(countsTowardQuota(row(createdAt = "2025-01-01 00:00:00Z"), now))
        assertFalse(countsTowardQuota(row(deletedAt = "2026-11-01 11:30:00Z"), now))
        assertFalse(countsTowardQuota(row(expiresAt = "2026-11-01 11:59:59Z"), now))
        assertTrue(countsTowardQuota(row(expiresAt = "2026-11-01T12:00:01.000Z"), now))
        assertTrue(countsTowardQuota(row(status = TempFileStatus.Pending, createdAt = "2026-11-01 10:30:00Z"), now))
        assertFalse(countsTowardQuota(row(status = TempFileStatus.Pending, createdAt = "2026-11-01 09:59:00Z"), now))
        assertEquals(15 * mb, TempFiles.usedBytes(listOf(row(), row(id = "b", size = 5 * mb), row(id = "c", deletedAt = "2026-11-01 11:00:00Z")), now))
    }

    @Test fun quotaProblems() {
        val q = TempFileRules.QUOTA_BYTES
        assertTrue(quotaProblem(0, emptyList(), now)!!.contains("kosong"))
        assertTrue(quotaProblem(q + 1, emptyList(), now)!!.contains("1 GB"))
        assertNull(quotaProblem(q, emptyList(), now))
        assertTrue(quotaProblem(q - 10 * mb + 1, listOf(row()), now)!!.contains("Kuota"))
        assertNull(quotaProblem(q - 10 * mb, listOf(row()), now))
        assertTrue(quotaProblem(600 * mb, emptyList(), now, 600 * mb)!!.contains("Kuota"))
        assertNull(quotaProblem(400 * mb, emptyList(), now, 600 * mb))
    }

    @Test fun stateAndLinks() {
        assertEquals(TempFileState.Ready, TempFiles.state(row(), now))
        assertEquals(TempFileState.Pending, TempFiles.state(row(status = TempFileStatus.Pending), now))
        assertEquals(TempFileState.Expired, TempFiles.state(row(expiresAt = "2026-11-01 11:00:00Z"), now))
        assertTrue(TempFiles.isShareable(row(), now))
        assertFalse(TempFiles.isShareable(row(isPublic = false), now))
        assertFalse(TempFiles.isShareable(row(status = TempFileStatus.Pending), now))
        val t = "A_b-".repeat(10) + "xyz"
        assertEquals("https://app.example/unduh/$t", TempFiles.shareUrl("https://app.example/", t))
        assertEquals(t, TempFiles.shareTokenFromPath(java.net.URI(TempFiles.shareUrl("https://app.example", t)).path))
        assertTrue(TempFiles.isPublicDownloadPath("/unduh/pendek"))
        assertTrue(TempFiles.isPublicDownloadPath("/unduh"))
        assertFalse(TempFiles.isPublicDownloadPath("/unduhan"))
        assertNull(TempFiles.shareTokenFromPath("/unduh/pendek"))
        assertEquals("https://p.supabase.co/functions/v1/tempfiles/d/$t", TempFiles.directDownloadUrl("https://p.supabase.co/", t))
    }

    @Test fun expiryLabels() {
        assertEquals("Tanpa batas waktu", expiryLabel(null, now))
        assertEquals("Kedaluwarsa", expiryLabel(at(-1), now))
        assertEquals("Kedaluwarsa dalam 1 menit", expiryLabel(at(30_000), now))
        assertEquals("Kedaluwarsa dalam 59 menit", expiryLabel(at(59 * 60_000), now))
        assertEquals("Kedaluwarsa dalam 5 jam", expiryLabel(at(5 * 3600_000), now))
        assertEquals("Kedaluwarsa dalam 47 jam", expiryLabel(at(47 * 3600_000), now))
        assertEquals("Kedaluwarsa dalam 7 hari", expiryLabel(at(7 * 86_400_000), now))
        assertEquals("Kedaluwarsa dalam 1 jam", expiryLabel(at(3600_000 - 1500), now))
        assertEquals("Kedaluwarsa dalam 7 hari", expiryLabel(at(7 * 86_400_000 - 5000), now))
        assertEquals("Kedaluwarsa dalam 1 menit", expiryLabel(at(1), now))
        assertEquals("Kedaluwarsa dalam 1 jam", expiryLabel("2026-11-01 13:00:00Z", now))
        assertEquals("Tanpa batas", expiryShort(null, now))
        assertEquals("Kedaluwarsa", expiryShort(at(0), now))
        assertEquals("Sisa 1 menit", expiryShort(at(30_000), now))
        assertEquals("Sisa 1 jam", expiryShort(at(3600_000 - 1500), now))
        assertEquals("Sisa 7 hari", expiryShort(at(7 * 86_400_000 - 5000), now))
        assertEquals("Sisa 6 hari", expiryShort(at(6 * 86_400_000L + 3 * 3600_000), now))
    }

    @Test fun overlay() {
        val local = listOf(row(id = "a", name = "lama.pdf"), row(id = "b"))
        val merged = TempFiles.mergeOverlay(local, mapOf(
            "a" to row(id = "a", name = "baru.pdf", updatedAt = "2026-11-01T11:05:00.123456+00:00"),
            "b" to row(id = "b", name = "basi.pdf", updatedAt = "2026-11-01T10:00:00+00:00"),
        ))
        assertEquals(listOf("baru.pdf", "a.pdf"), merged.map { it.fileName })
        assertEquals(listOf("b"), TempFiles.mergeOverlay(listOf(row(id = "a"), row(id = "b")), mapOf("a" to null)).map { it.id })
        val overlay = mapOf(
            "a" to row(id = "a", updatedAt = "2026-11-01T11:05:00+00:00"),
            "b" to row(id = "b", updatedAt = "2026-11-01T11:05:00+00:00"),
            "gone" to null, "stillThere" to null, "fresh" to row(id = "fresh"),
        )
        val l = listOf(row(id = "a", updatedAt = "2026-11-01 11:05:00Z"), row(id = "b", updatedAt = "2026-11-01 11:00:00Z"), row(id = "stillThere"))
        assertEquals(listOf("a", "gone"), TempFiles.settledOverlayIds(l, overlay).sorted())
        // berkas yang baru dipesan (belum tersinkron) tampil paling atas
        assertEquals(listOf("fresh", "a", "b"), TempFiles.withOverlay(listOf(row(id = "a"), row(id = "b")), mapOf("fresh" to row(id = "fresh"))).map { it.id })
    }

    @Test fun kinds() {
        assertEquals(FileKind.Image, TempFiles.kind("image/png", "x.png"))
        assertEquals(FileKind.Archive, TempFiles.kind("application/octet-stream", "arsip.ZIP"))
        assertEquals(FileKind.Sheet, TempFiles.kind("application/octet-stream", "data.csv"))
        assertEquals(FileKind.Document, TempFiles.kind("application/pdf", "a.pdf"))
        assertEquals(FileKind.Other, TempFiles.kind("application/octet-stream", "Makefile"))
        listOf("setup.exe", "app.APK", "run.sh ", "x.ps1").forEach { assertTrue(TempFiles.isExecutableName(it), it) }
        listOf("foto.jpg", "exe.txt", "laporan.pdf").forEach { assertFalse(TempFiles.isExecutableName(it), it) }
    }
}
