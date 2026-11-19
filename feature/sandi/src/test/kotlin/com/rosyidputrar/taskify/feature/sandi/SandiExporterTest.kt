package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiExport
import com.rosyidputrar.taskify.core.domain.sandi.TotpAlgorithm
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandiExporterTest {
    private val items = listOf(
        ItemPayload("GitHub", "https://github.com", "rosyid@example.com", "Kopi-42!", totp = TotpConfig("JBSWY3DPEHPK3PXP")),
        ItemPayload("Bank, Utama", "jago.com", "rosyid", "a,b\"c"),
        ItemPayload("Cloudflare", "https://dash.cloudflare.com", "ops", "", totp = TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", period = 60)),
        ItemPayload("Steam", "", "gamer", "", totp = TotpConfig("KRSXG5CTMVRXEZLUKN2XAZLSKNSWG4TFOQ", algorithm = TotpAlgorithm.SHA256)),
    )

    private class Harness(scope: TestScope, private val correct: String = "master benar", fail: Boolean = false) {
        val written = mutableListOf<Pair<String, String>>()
        val messages = mutableListOf<String>()
        var verified = 0
        val exporter = SandiExporter(
            scope = scope,
            verify = { verified++; it == correct },
            writer = { uri, bytes ->
                if (fail) throw IOException("penyimpanan penuh")
                written += uri to String(bytes.copyOf(), Charsets.UTF_8)
            },
            show = { messages += it },
            today = { "2026-11-19" },
            batchId = { 42 },
        )
    }

    @Test fun wrongOrEmptyPasswordExportsNothing() = runTest {
        val h = Harness(this)
        assertEquals("Master password salah.", assertFailsWith<SandiUserException> { h.exporter.start(ExportKind.GoogleCsv, "salah", items) }.message)
        assertEquals("Masukkan master password.", assertFailsWith<SandiUserException> { h.exporter.start(ExportKind.GoogleCsv, "", items) }.message)
        assertEquals(1, h.verified) // sandi kosong tidak sampai ke pengecekan (PBKDF2 mahal)
        assertEquals(SandiExporter.State(), h.exporter.state.value)
        h.exporter.finish("content://x")
        advanceUntilIdle()
        assertTrue(h.written.isEmpty())
    }

    @Test fun csvIsWrittenOnceToChosenLocationThenForgotten() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.GoogleCsv, "master benar", items)
        val pending = assertNotNull(h.exporter.state.value.pending)
        assertEquals(PendingExport(ExportKind.GoogleCsv, "taskify-sandi-2026-11-19.csv", "text/csv", 2), pending)

        h.exporter.launched()
        assertTrue(h.exporter.state.value.pending!!.launched)

        h.exporter.finish("content://downloads/1")
        advanceUntilIdle()
        assertEquals(listOf("content://downloads/1" to SandiExport.passwordsCsv(items)), h.written)
        assertEquals(listOf("2 sandi diekspor. Hapus berkasnya setelah diimpor."), h.messages)
        assertNull(h.exporter.state.value.pending)

        // Hasil pemilih yang datang dua kali tidak menulis ulang.
        h.exporter.finish("content://downloads/2")
        advanceUntilIdle()
        assertEquals(1, h.written.size)
    }

    @Test fun otpauthListKeepsEveryKeyIncludingNonStandard() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.OtpauthList, "master benar", items)
        assertEquals(PendingExport(ExportKind.OtpauthList, "taskify-2fa-2026-11-19.txt", "text/plain", 3), h.exporter.state.value.pending)
        h.exporter.finish("content://f")
        advanceUntilIdle()
        assertEquals(3, h.written.single().second.lines().count { it.startsWith("otpauth://totp/") })
        assertEquals("3 kunci 2FA diekspor. Hapus berkasnya setelah diimpor.", h.messages.single())
    }

    @Test fun cancelledPickerDropsContent() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.GoogleCsv, "master benar", items)
        h.exporter.finish(null)
        assertNull(h.exporter.state.value.pending)
        h.exporter.finish("content://late")
        advanceUntilIdle()
        assertTrue(h.written.isEmpty())
        assertEquals(listOf("Ekspor kedaluwarsa. Ulangi dari Pengaturan brankas."), h.messages)
    }

    @Test fun unusedExportExpires() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.GoogleCsv, "master benar", items)
        advanceTimeBy(SandiExporter.TTL_MS - 1)
        assertNotNull(h.exporter.state.value.pending)
        advanceTimeBy(2)
        assertNull(h.exporter.state.value.pending)
        h.exporter.finish("content://late")
        advanceUntilIdle()
        assertTrue(h.written.isEmpty())
        assertEquals(listOf("Ekspor kedaluwarsa. Ulangi dari Pengaturan brankas."), h.messages)
    }

    @Test fun writeFailureIsReported() = runTest {
        val h = Harness(this, fail = true)
        h.exporter.start(ExportKind.GoogleCsv, "master benar", items)
        h.exporter.finish("content://full")
        advanceUntilIdle()
        assertEquals(listOf("Berkas tidak bisa ditulis: penyimpanan penuh"), h.messages)
        assertNull(h.exporter.state.value.pending)
    }

    @Test fun googleAuthenticatorQrOnlyStandardKeysAndGoneWhenLocked() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.GoogleAuthenticator, "master benar", items)
        val plan = assertNotNull(h.exporter.state.value.migration)
        assertEquals(1, plan.accounts)
        assertEquals(1, plan.codes.size)
        assertEquals(listOf("Cloudflare", "Steam"), plan.skipped.map { it.first })
        assertNull(h.exporter.state.value.pending) // QR tidak pernah jadi berkas

        h.exporter.onLocked()
        assertNull(h.exporter.state.value.migration)
    }

    @Test fun nothingToExport() = runTest {
        val h = Harness(this)
        val onlyNonStandard = listOf(items[2], items[3])
        assertEquals("Tidak ada kunci 2FA yang bisa dipindahkan ke Google Authenticator.",
            assertFailsWith<SandiUserException> { h.exporter.start(ExportKind.GoogleAuthenticator, "master benar", onlyNonStandard) }.message)
        assertEquals("Tidak ada sandi untuk diekspor.",
            assertFailsWith<SandiUserException> { h.exporter.start(ExportKind.GoogleCsv, "master benar", onlyNonStandard) }.message)
        assertEquals("Tidak ada kunci 2FA untuk diekspor.",
            assertFailsWith<SandiUserException> { h.exporter.start(ExportKind.OtpauthList, "master benar", listOf(items[1])) }.message)
        assertEquals(SandiExporter.State(), h.exporter.state.value)
    }

    @Test fun clearForgetsEverything() = runTest {
        val h = Harness(this)
        h.exporter.start(ExportKind.GoogleCsv, "master benar", items)
        h.exporter.clear()
        assertEquals(SandiExporter.State(), h.exporter.state.value)
        h.exporter.finish("content://x")
        advanceUntilIdle()
        assertTrue(h.written.isEmpty())
    }
}
