package com.rosyidputrar.taskify.core.domain.sandi

import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthenticatorImportTest {
    /**
     * Dibuat dengan library protobuf RESMI (Python, deskriptor MigrationPayload), bukan oleh kode
     * Taskify: 7 akun (termasuk HOTP, MD5, kunci pendek, label "Penerbit:akun", Unicode tanpa kolom
     * bawaan) + 2 kolom tak dikenal di akhir payload.
     */
    private val official = "otpauth-migration://offline?data=Ci4KCkhlbGxvId6tvu8SEnJvc3lpZEBleGFtcGxlLmNvbRoGR2l0SHViIAEoATACCjEKFDEyMzQ1Njc4OTAxMjM0NTY3ODkwEhNCYW5rIEphZ286bmFzYWJhaDAxIAIoAjACCiMKCmFiY2RlZmdoaWoSA29wcxoKQ2xvdWRmbGFyZSADKAEwAgolCgphYmNkZWZnaGlqEglob3RwLXVzZXIaBExhbWEgASgBMAE4BwodCgphYmNkZWZnaGlqEgNtZDUaBEt1bm8gBCgBMAIKGwoDYWJjEgZwZW5kZWsaBlBlbmRlayABKAEwAgoyChhrdW5jaS3DvPCflJBrdW5jaS3DvPCflJASC8OpbW9qaSDwn5SQGgnDnG7Dr2NvZGUQARgCIAEosvIZfQECAwSCAQN4eXo%3D"

    @Test fun decodesOfficialProtobufPayload() {
        val scan = AuthenticatorImport.parse(official).single()
        assertEquals(424242L, scan.batchId)
        assertEquals(1, scan.batchIndex)
        assertEquals(2, scan.batchSize)
        assertEquals(
            listOf(
                AuthAccount("GitHub", "rosyid@example.com", TotpConfig("JBSWY3DPEHPK3PXP")),
                // Penerbit kosong + nama "Penerbit:akun" → dipisah.
                AuthAccount("Bank Jago", "nasabah01", TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 8, 30, TotpAlgorithm.SHA256)),
                AuthAccount("Cloudflare", "ops", TotpConfig("MFRGGZDFMZTWQ2LK", 6, 30, TotpAlgorithm.SHA512)),
                // Kolom bawaan (0) tidak dikirim protobuf → SHA1, 6 digit, TOTP.
                AuthAccount("Ünïcode", "émoji 🔐", TotpConfig(Totp.encodeBase32("kunci-ü🔐kunci-ü🔐".toByteArray()))),
            ),
            scan.accounts,
        )
        assertEquals(
            listOf(
                "Lama (hotp-user)" to "HOTP (berbasis penghitung) belum didukung",
                "Kuno (md5)" to "algoritme MD5 tidak didukung",
                "Pendek (pendek)" to "kunci terlalu pendek",
            ),
            scan.unsupported,
        )
        // Kode yang dihasilkan memakai algoritme/digit asli.
        assertEquals(8, Totp.window(scan.accounts[1].config, 59_000)!!.code.length)
    }

    @Test fun roundTripsTaskifyOwnExportIncludingBatches() {
        val items = (1..20).map { n -> ItemPayload("Akun %02d".format(n), username = "user$n@example.com", totp = TotpConfig(Totp.encodeBase32(ByteArray(20) { (it * 3 + n).toByte() }))) }
        val codes = SandiExport.googleMigration(items, batchId = 777).codes
        assertEquals(3, codes.size)
        val scans = codes.flatMap(AuthenticatorImport::parse)
        assertEquals(items.map { AuthAccount(it.title, it.username, it.totp!!) }, scans.flatMap { it.accounts })
        assertEquals(emptyList(), AuthenticatorImport.missing(scans))
        // Baru QR 1 & 3 yang dipindai → QR 2 (indeks 1) belum.
        assertEquals(listOf(1), AuthenticatorImport.missing(listOf(scans[0], scans[2])))
    }

    @Test fun plainOtpauthLinesAreAccepted() {
        val scans = AuthenticatorImport.parse(
            "otpauth://totp/GitHub:rosyid?secret=JBSWY3DPEHPK3PXP&issuer=GitHub\n  otpauth://hotp/Lama:x?secret=JBSWY3DPEHPK3PXP&counter=1",
        )
        assertEquals(listOf(AuthAccount("GitHub", "rosyid", TotpConfig("JBSWY3DPEHPK3PXP"))), scans.flatMap { it.accounts })
        assertEquals(1, scans.flatMap { it.unsupported }.size)
        assertEquals(emptyList(), AuthenticatorImport.missing(scans))
    }

    @Test fun badInputIsAClearErrorNeverACrash() {
        fun fails(text: String) = assertFailsWith<ImportException> { AuthenticatorImport.parse(text) }.message!!
        assertEquals("Tidak ada teks QR.", fails("  "))
        assertTrue(fails("https://example.com").startsWith("Ini bukan QR authenticator"))
        assertTrue(fails("otpauth-migration://offline?data=%%%").contains("rusak") || fails("otpauth-migration://offline?data=%%%").contains("dibaca"))
        assertTrue(fails("otpauth-migration://offline?data=@@@").contains("base64"))
        assertTrue(fails("otpauth-migration://online?data=AA").contains("tidak dikenali"))
        assertTrue(fails("otpauth-migration://offline").contains("tidak berisi data"))
        // Panjang kolom melebihi isi.
        val truncated = Base64.getEncoder().encodeToString(byteArrayOf(0x0a, 0x7f, 0x0a, 0x01))
        assertTrue(fails("otpauth-migration://offline?data=$truncated").contains("rusak"))
        assertEquals("QR transfer akun kosong.", fails("otpauth-migration://offline?data=" + Base64.getEncoder().encodeToString(byteArrayOf(0x10, 0x01))))
    }

    @Test fun randomBytesNeverCrash() {
        val rnd = Random(42)
        repeat(3_000) {
            val data = Base64.getEncoder().encodeToString(ByteArray(rnd.nextInt(0, 200)) { rnd.nextInt().toByte() })
            try {
                AuthenticatorImport.parse("otpauth-migration://offline?data=$data")
            } catch (_: ImportException) {
                // diharapkan untuk data acak
            }
        }
    }

    @Test fun splitLabel() {
        assertEquals("GitHub" to "rosyid", AuthenticatorImport.splitLabel("", "GitHub:rosyid"))
        assertEquals("GitHub" to "rosyid", AuthenticatorImport.splitLabel("GitHub", "github: rosyid"))
        assertEquals("Bank" to "Lain:akun", AuthenticatorImport.splitLabel("Bank", "Lain:akun"))
        assertEquals("GitHub" to "rosyid", AuthenticatorImport.splitLabel("GitHub", "rosyid"))
    }

    // ------------------------------------------------------------------ rencana

    private val github = AuthAccount("GitHub", "rosyid@example.com", TotpConfig("JBSWY3DPEHPK3PXP"))
    private val jago = AuthAccount("Jago", "nasabah01", TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
    private val steam = AuthAccount("Steam", "gamer", TotpConfig("MFRGGZDFMZTWQ2LK"))

    @Test fun planAttachesToTheOneMatchingPasswordAndNeverOverwrites() {
        val existing = listOf(
            ItemPayload("github.com", "https://github.com/login", "Rosyid@Example.com", "Kopi-42!"), // cocok (host + pengguna)
            ItemPayload("Bank Jago", "https://jago.com", "nasabah01", "x"), // cocok lewat host jago.com
            ItemPayload("Steam", "", "gamer", "a", totp = TotpConfig("KRSXG5CTMVRXEZLUKN2XAZLSKNSWG4TFOQ")), // sudah punya 2FA lain → tidak disentuh
        )
        val plan = AuthenticatorImport.plan(listOf(AuthScan(listOf(github, jago, steam), emptyList())), existing, attach = true)
        assertEquals(listOf(0, 1), plan.attach.map { it.first })
        assertEquals(github.config, plan.attach[0].second.totp)
        assertEquals("Kopi-42!", plan.attach[0].second.password) // isi lain tetap
        assertEquals(listOf(ItemPayload("Steam", username = "gamer", totp = steam.config)), plan.add)
        assertEquals(0, plan.identical)
    }

    @Test fun planSkipsKnownKeysDuplicatesAndAmbiguousMatches() {
        val existing = listOf(
            ItemPayload("GitHub", "https://github.com", "rosyid@example.com", "p1"),
            ItemPayload("GitHub kerja", "https://github.com", "rosyid@example.com", "p2"), // dua kandidat → tidak ditebak
            ItemPayload("Jago", "", "nasabah01", "", totp = jago.config), // kunci sama persis sudah ada
        )
        val scans = listOf(AuthScan(listOf(github, jago), emptyList()), AuthScan(listOf(github), emptyList()))
        val plan = AuthenticatorImport.plan(scans, existing, attach = true)
        assertEquals(1, plan.identical)
        assertEquals(1, plan.duplicates)
        assertEquals(emptyList(), plan.attach)
        assertEquals(listOf("GitHub"), plan.add.map { it.title })

        // Penggabungan dimatikan → selalu item baru.
        val separate = AuthenticatorImport.plan(listOf(AuthScan(listOf(github), emptyList())), existing.take(1), attach = false)
        assertEquals(emptyList(), separate.attach)
        assertEquals(1, separate.add.size)
    }

    @Test fun matchingIsStrict() {
        val item = ItemPayload("GitHub", "https://github.com", "rosyid@example.com", "p")
        assertTrue(AuthenticatorImport.matches(item, github))
        assertTrue(!AuthenticatorImport.matches(item.copy(username = "orang-lain"), github))
        assertTrue(!AuthenticatorImport.matches(item, github.copy(account = "")))
        assertTrue(!AuthenticatorImport.matches(ItemPayload("Gitlab", "https://gitlab.com", "rosyid@example.com", "p"), github))
    }
}
