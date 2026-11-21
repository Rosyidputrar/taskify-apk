package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiExport
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatorImporterTest {
    /** Ekspor Google Authenticator 20 akun → 3 QR (dibuat dengan penyandi Taskify sendiri). */
    private val accounts = (1..20).map { n -> ItemPayload("Akun %02d".format(n), username = "user$n@example.com", totp = TotpConfig(Totp.encodeBase32(ByteArray(20) { (it * 5 + n).toByte() }))) }
    private val codes = SandiExport.googleMigration(accounts, batchId = 99).codes

    private val vault = listOf(
        SandiItem("id-a1", ItemPayload("Akun 01", "", "user1@example.com", "sandi-1"), "", ""), // cocok → dipasangi 2FA
        SandiItem("id-a2", ItemPayload("Akun 02", "", "", "", totp = accounts[1].totp), "", ""), // kunci sama → dilewati
    )

    private class Harness(scope: TestScope) {
        val saves = mutableListOf<Pair<List<ItemPayload>, List<Pair<String, ItemPayload>>>>()
        val messages = mutableListOf<String>()
        val importer = AuthenticatorImporter(scope, { add, attach -> saves += add to attach; SandiService.ImportResult(add.size, emptyList(), attach.size) }, { messages += it })
    }

    @Test fun collectsBatchesAndReportsMissingQr() = runTest {
        val h = Harness(this)
        h.importer.open()
        assertEquals(AuthImportUi(), h.importer.summary(emptyList()))
        assertTrue(h.importer.add(codes[0]))
        assertTrue(h.importer.add(codes[2]))
        assertFalse(h.importer.add(codes[2])) // QR yang sama dua kali
        val ui = h.importer.summary(emptyList())!!
        assertEquals(listOf(2), ui.missing)
        assertEquals(3, ui.batchSize)
        assertEquals(12, ui.add)
        assertEquals(listOf("8 akun terbaca. Masih ada 2 QR lagi.", "4 akun terbaca. Masih ada 1 QR lagi.", "QR ini sudah dipindai."), h.messages)
        h.importer.add(codes[1])
        assertEquals(emptyList(), h.importer.summary(emptyList())!!.missing)
        assertEquals("8 akun terbaca.", h.messages.last())
    }

    @Test fun summaryAndConfirmAttachToMatchingPasswordAndSkipKnownKeys() = runTest {
        val h = Harness(this)
        h.importer.open()
        codes.forEach { h.importer.add(it) }
        val ui = h.importer.summary(vault.map { it.payload })!!
        assertEquals(listOf("Akun 01 · user1@example.com"), ui.attach)
        assertEquals(1, ui.identical)
        assertEquals(18, ui.add)
        assertTrue(ui.attachEnabled && ui.attachOn)

        h.importer.confirm(vault)
        val (add, attach) = h.saves.single()
        assertEquals(18, add.size)
        assertEquals(listOf("id-a1"), attach.map { it.first })
        assertEquals(accounts[0].totp, attach.single().second.totp)
        assertEquals("sandi-1", attach.single().second.password)
        assertTrue(h.messages.last().startsWith("19 akun 2FA diimpor (1 dipasang ke sandi yang sudah ada)."))
        assertNull(h.importer.summary(emptyList())) // ditutup & dilupakan
    }

    @Test fun attachCanBeTurnedOff() = runTest {
        val h = Harness(this)
        h.importer.open()
        h.importer.add(codes[0])
        h.importer.setAttach(false)
        val ui = h.importer.summary(vault.map { it.payload })!!
        assertEquals(emptyList(), ui.attach)
        assertTrue(ui.attachEnabled) // sakelar tetap tampil supaya bisa dinyalakan lagi
        assertFalse(ui.attachOn)
        h.importer.confirm(vault)
        assertEquals(emptyList(), h.saves.single().second)
        assertEquals(7, h.saves.single().first.size)
    }

    @Test fun nothingNewOrNothingScanned() = runTest {
        val h = Harness(this)
        h.importer.open()
        assertEquals("Belum ada QR yang dipindai.", assertFailsWith<SandiUserException> { h.importer.confirm(vault) }.message)
        h.importer.add(SandiExport.googleMigration(listOf(accounts[1]), batchId = 5).codes.single())
        h.importer.confirm(vault)
        assertTrue(h.saves.isEmpty())
        assertEquals("Tidak ada kunci 2FA baru — semuanya sudah ada di brankas.", h.messages.last())
    }

    /** D-10: sesi impor dibatasi 100 akun; label hasil pindai tampil di ringkasan sebelum konfirmasi. */
    @Test fun sessionCapAndScannedLabels() = runTest {
        val h = Harness(this)
        h.importer.open()
        fun uri(n: Int): String {
            val secret = Totp.encodeBase32(ByteArray(10).apply { this[0] = n.toByte(); this[1] = (n shr 8).toByte() })
            return "otpauth://totp/Situs$n:user$n?secret=$secret&issuer=Situs$n"
        }
        // 10 batch BERBEDA (total 100 akun berbeda) — batch yang sama dua kali ter-dedup oleh plan.
        repeat(10) { i ->
            val batch = (1..10).joinToString("\n") { uri(i * 10 + it) }
            assertTrue(h.importer.add(batch))
        }
        val ui = h.importer.summary(emptyList())!!
        assertEquals(100, ui.add)
        assertTrue(ui.scannedLabels.contains("Situs1 (user1)"))
        assertEquals(10, ui.scannedLabels.size, "hanya 10 label pertama ditampilkan")
        assertFalse(h.importer.add(uri(101)))
        assertTrue(h.messages.last().contains("Terlalu banyak akun"))
        assertEquals(100, h.importer.summary(emptyList())!!.add, "impor yang sudah terbaca tetap tersimpan")
    }

    @Test fun badTextIsAMessageAndIdleImportExpires() = runTest {
        val h = Harness(this)
        h.importer.open()
        assertFalse(h.importer.add("https://bukan-qr.example"))
        assertTrue(h.messages.single().startsWith("Ini bukan QR authenticator"))
        h.importer.add(codes[0])
        advanceTimeBy(AuthenticatorImporter.TTL_MS + 1)
        assertNull(h.importer.summary(emptyList()))
        // Dibuka lagi = mulai dari kosong.
        h.importer.open()
        assertEquals(0, h.importer.summary(emptyList())!!.scanned)
    }

    @Test fun errorCallbackReceivesRejectionSeparatelyFromInfoShow() = runTest {
        val infos = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val importer = AuthenticatorImporter(
            scope = this,
            save = { add, attach -> SandiService.ImportResult(add.size, emptyList(), attach.size) },
            show = { infos += it },
            onError = { errors += it },
        )
        importer.open()
        assertFalse(importer.add("https://bukan-qr.example"))
        assertTrue(infos.isEmpty())
        assertTrue(errors.single().startsWith("Ini bukan QR authenticator"))
        assertTrue(importer.add(codes[0]))
        assertEquals(1, infos.size)
        assertFalse(importer.add(codes[0]))
        assertEquals("QR ini sudah dipindai.", errors.last())
    }
}
