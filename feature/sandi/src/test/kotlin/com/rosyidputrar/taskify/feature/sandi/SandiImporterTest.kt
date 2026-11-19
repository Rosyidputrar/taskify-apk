package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.domain.sandi.ImportFormat
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandiImporterTest {
    private val csv = listOf(
        "name,url,username,password,note",
        "github.com,https://github.com/login,rosyid@example.com,Kopi-42!,",
        "tokopedia.com,https://www.tokopedia.com/,0812,sandi-baru,",
        "jago.com,https://jago.com/,rosyid,x9#Lm2,",
        "kosong.com,https://kosong.com/,user,,",
    ).joinToString("\r\n")

    private val existing = listOf(
        ItemPayload("GitHub", "https://github.com", "rosyid@example.com", "Kopi-42!"),
        ItemPayload("Tokopedia", "https://www.tokopedia.com", "0812", "sandi-lama"),
    )

    private class Harness(scope: TestScope, text: String? = null, failRead: Boolean = false, canDelete: Boolean = true, tooLarge: List<String> = emptyList()) {
        val saved = mutableListOf<List<ItemPayload>>()
        val deleted = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val importer = SandiImporter(
            scope = scope,
            documents = object : SandiDocuments {
                override suspend fun read(uri: String, maxBytes: Int): String = if (failRead) throw IOException("izin dicabut") else text!!
                override suspend fun delete(uri: String): Boolean { deleted += uri; return canDelete }
            },
            save = { items -> saved += items; SandiService.ImportResult(items.size - tooLarge.size, tooLarge) },
            show = { messages += it },
        )
    }

    @Test fun pickerIsRequestedOnceAndCancelLeavesNothing() = runTest {
        val h = Harness(this, csv)
        h.importer.pick()
        assertEquals(SandiImporter.Picking(), h.importer.state.value.picking)
        h.importer.launched()
        assertTrue(h.importer.state.value.picking!!.launched)
        h.importer.open(null)
        assertEquals(SandiImporter.State(), h.importer.state.value)
        assertNull(h.importer.preview(existing))
    }

    @Test fun previewFollowsCurrentVaultContents() = runTest {
        val h = Harness(this, csv)
        h.importer.open("content://csv/1")
        assertNotNull(h.importer.state.value.pending)
        // Brankas sempat terkunci (isi kosong) → semua tampak baru.
        assertEquals(3, h.importer.preview(emptyList())!!.add)
        // Setelah dibuka: GitHub sama persis, Tokopedia beda sandi (tetap ditambahkan, tidak menimpa).
        val p = h.importer.preview(existing)!!
        assertEquals(ImportFormat.Google, p.format)
        assertEquals(2, p.add)
        assertEquals(1, p.identical)
        assertEquals(1, p.differentPassword)
        assertEquals(1, p.noSecret)
        assertEquals(listOf("tokopedia.com", "jago.com"), p.sample)
    }

    @Test fun confirmSavesAgainstLatestVaultAndOffersToDeleteFile() = runTest {
        val h = Harness(this, csv)
        h.importer.open("content://csv/1")
        h.importer.confirm(existing)
        assertEquals(listOf("tokopedia.com", "jago.com"), h.saved.single().map { it.title })
        assertEquals(ImportDone(2, emptyList()), h.importer.state.value.done)
        assertNull(h.importer.state.value.pending)
        assertNull(h.importer.preview(existing)) // isi berkas sudah dilupakan
        // Konfirmasi kedua (mis. ketuk dua kali) tidak menyimpan ulang.
        assertEquals("Impor kedaluwarsa. Pilih berkasnya lagi.", assertFailsWith<SandiUserException> { h.importer.confirm(existing) }.message)
        h.importer.deleteSource()
        assertEquals(listOf("content://csv/1"), h.deleted)
        assertEquals(listOf("Berkas CSV dihapus."), h.messages)
        assertNull(h.importer.state.value.done)
    }

    @Test fun nothingNewSavesNothing() = runTest {
        val h = Harness(this, "name,url,username,password\nGitHub,https://github.com,rosyid@example.com,Kopi-42!\n")
        h.importer.open("content://csv/2")
        h.importer.confirm(existing)
        assertTrue(h.saved.isEmpty())
        assertEquals(listOf("Tidak ada sandi baru — semuanya sudah ada di brankas."), h.messages)
        assertEquals(SandiImporter.State(), h.importer.state.value)
    }

    @Test fun badFilesBecomeMessages() = runTest {
        val notCsv = Harness(this, "Tanggal;Nominal\n2026-11-01;5000")
        notCsv.importer.open("content://x")
        assertTrue(notCsv.messages.single().startsWith("Ini bukan berkas sandi"))
        assertNull(notCsv.importer.state.value.pending)

        val unreadable = Harness(this, failRead = true)
        unreadable.importer.open("content://y")
        assertEquals(listOf("Berkas tidak bisa dibaca: izin dicabut"), unreadable.messages)
    }

    @Test fun tooLargeItemsAndUndeletableFileAreReported() = runTest {
        val h = Harness(this, csv, canDelete = false, tooLarge = listOf("jago.com"))
        h.importer.open("content://csv/3")
        h.importer.confirm(existing)
        assertEquals(ImportDone(1, listOf("jago.com")), h.importer.state.value.done)
        h.importer.deleteSource()
        assertEquals(listOf("Berkas tidak bisa dihapus dari sini. Hapus manual lewat aplikasi Berkas."), h.messages)
    }

    @Test fun unconfirmedImportExpiresAndCancelForgets() = runTest {
        val h = Harness(this, csv)
        h.importer.open("content://csv/4")
        advanceTimeBy(SandiImporter.TTL_MS + 1)
        assertNull(h.importer.state.value.pending)
        assertNull(h.importer.preview(existing))

        h.importer.open("content://csv/5")
        h.importer.cancel()
        assertNull(h.importer.state.value.pending)
        assertNull(h.importer.preview(existing))
        h.importer.dismissDone()
        h.importer.deleteSource() // tidak ada sumber → tidak menghapus apa pun
        assertTrue(h.deleted.isEmpty())
    }
}
