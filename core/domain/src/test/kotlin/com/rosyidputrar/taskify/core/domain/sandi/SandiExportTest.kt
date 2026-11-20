package com.rosyidputrar.taskify.core.domain.sandi

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandiExportTest {
    private fun item(title: String, url: String = "", user: String = "", pass: String = "", notes: String = "", totp: TotpConfig? = null) =
        ItemPayload(title = title, url = url, username = user, password = pass, notes = notes, totp = totp)

    // ------------------------------------------------------------------ CSV

    /** Parser RFC 4180 minimal (independen dari penulis) untuk membaca balik hasil ekspor. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var i = 0
        var quoted = false
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i += 2; continue }
                if (c == '"') { quoted = false; i++; continue }
                field.append(c); i++; continue
            }
            when {
                c == '"' && field.isEmpty() -> { quoted = true; i++ }
                c == ',' -> { row += field.toString(); field.clear(); i++ }
                c == '\r' && i + 1 < text.length && text[i + 1] == '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf(); i += 2 }
                else -> { field.append(c); i++ }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    @Test fun csvHasChromeHeaderCrlfAndNoBom() {
        val csv = SandiExport.passwordsCsv(listOf(item("GitHub", "https://github.com/login", "rosyid@example.com", "Kopi-42!")))
        assertEquals("name,url,username,password,note\r\nGitHub,https://github.com/login,rosyid@example.com,Kopi-42!,\r\n", csv)
        assertFalse(csv.startsWith("﻿"))
    }

    @Test fun csvRoundTripsTrickyValuesUnchanged() {
        val tricky = listOf(
            item("Bank, Utama", "jago.com", "rosyid \"rp\"", "a,b\"c\nd\r\ne", "baris 1\nbaris 2"),
            // Diawali karakter formula: sandi TIDAK boleh diberi awalan ' (jadi salah saat
            // diimpor); username & catatan justru diberi awalan (D-08).
            item("Formula", "https://example.com", "=user", "=SUM(A1)+@cmd", "-catatan"),
            item("Spasi", "https://spasi.example.com", " lead", "trail ", "\tTab"),
            item("Emoji 🔐", "https://emoji.example.com", "üser", "pässwörd🔑", ""),
        )
        val rows = parseCsv(SandiExport.passwordsCsv(tricky))
        assertEquals(SandiExport.CSV_HEADER, rows.first())
        val byName = rows.drop(1).associateBy { it[0] }
        assertEquals(4, byName.size)
        assertEquals(listOf("Bank, Utama", "https://jago.com", "rosyid \"rp\"", "a,b\"c\nd\r\ne", "baris 1\nbaris 2"), byName.getValue("Bank, Utama"))
        // D-08: sandi TIDAK boleh diberi awalan ' (jadi salah saat diimpor), tapi kolom
        // non-sandi yang diawali karakter formula diberi awalan ' anti-formula spreadsheet.
        assertEquals(listOf("Formula", "https://example.com", "'=user", "=SUM(A1)+@cmd", "'-catatan"), byName.getValue("Formula"))
        assertEquals(listOf("Spasi", "https://spasi.example.com", " lead", "trail ", "\tTab"), byName.getValue("Spasi"))
        assertEquals(listOf("Emoji 🔐", "https://emoji.example.com", "üser", "pässwörd🔑", ""), byName.getValue("Emoji 🔐"))
    }

    @Test fun antiFormulaPrefixesOnlyFormulaLikeNonPasswordFields() {
        assertTrue(SandiExport.startsWithFormulaChar("=2+5"))
        assertTrue(SandiExport.startsWithFormulaChar("+62812"))
        assertTrue(SandiExport.startsWithFormulaChar("-2"))
        assertTrue(SandiExport.startsWithFormulaChar("@user"))
        assertTrue(SandiExport.startsWithFormulaChar("  =cmd")) // spasi awal tidak melindungi
        assertFalse(SandiExport.startsWithFormulaChar("GitHub"))
        assertFalse(SandiExport.startsWithFormulaChar(""))
        assertFalse(SandiExport.startsWithFormulaChar("a=b"))
        assertEquals("'=SUM(A1)", SandiExport.antiFormula("=SUM(A1)"))
        assertEquals("biasa", SandiExport.antiFormula("biasa"))
        // Judul & catatan ikut diberi awalan; sandi tidak pernah disentuh.
        val rows = parseCsv(SandiExport.passwordsCsv(listOf(item("=Jebakan", "https://x.example.com", "u", "=sandi", "@catatan")))).drop(1)
        assertEquals(listOf("'=Jebakan", "https://x.example.com", "u", "=sandi", "'@catatan"), rows.single())
    }

    @Test fun csvSkipsItemsWithoutPasswordAndNormalisesUrls() {
        val items = listOf(
            item("Hanya 2FA", "https://cloudflare.com", "ops", "", totp = TotpConfig("JBSWY3DPEHPK3PXP")),
            item("Tanpa skema", "www.tokopedia.com/login", "0812", "x"),
            item("Aplikasi", "androidapp://com.bca", "rosyid", "y"),
            item("Wi-Fi Rumah", "", "", "RumahKita2026"),
            item("", "https://dash.example.com/a", "u", "z"),
        )
        val rows = parseCsv(SandiExport.passwordsCsv(items)).drop(1)
        assertEquals(setOf("Aplikasi", "dash.example.com", "Tanpa skema", "Wi-Fi Rumah"), rows.map { it[0] }.toSet())
        val byName = rows.associateBy { it[0] }
        assertEquals("https://www.tokopedia.com/login", byName.getValue("Tanpa skema")[1])
        // Bukan alamat web: ditulis apa adanya (Bitwarden dkk. mengenali androidapp://).
        assertEquals("androidapp://com.bca", byName.getValue("Aplikasi")[1])
        assertEquals("", byName.getValue("Wi-Fi Rumah")[1])
        // Judul kosong → host situs sebagai nama.
        assertEquals("https://dash.example.com/a", byName.getValue("dash.example.com")[1])

        assertEquals(CsvSummary(rows = 4, skippedNoPassword = 1, withoutWebAddress = 2), SandiExport.csvSummary(items))
    }

    @Test fun csvOrderIsStableByTitle() {
        val csv = SandiExport.passwordsCsv(listOf(item("zeta", pass = "1"), item("Alpha", pass = "2"), item("beta", pass = "3")))
        assertEquals(listOf("Alpha", "beta", "zeta"), parseCsv(csv).drop(1).map { it[0] })
    }

    @Test fun csvFieldQuotesOnlyWhenNeeded() {
        assertEquals("plain", SandiExport.csvField("plain"))
        assertEquals("", SandiExport.csvField(""))
        assertEquals("\"a,b\"", SandiExport.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", SandiExport.csvField("say \"hi\""))
        assertEquals("\" x\"", SandiExport.csvField(" x"))
        assertEquals("=1+1", SandiExport.csvField("=1+1"))
    }

    // ------------------------------------------------------------------ otpauth://

    @Test fun otpauthListRoundTripsEveryKeyExactly() {
        val items = listOf(
            item("GitHub", "https://github.com", "rosyid@example.com", totp = TotpConfig("JBSWY3DPEHPK3PXP")),
            item("Bank: Utama", "", "nasabah 01", totp = TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", digits = 8, period = 60, algorithm = TotpAlgorithm.SHA512)),
            item("", "https://dash.cloudflare.com", "", totp = TotpConfig("KRSXG5CTMVRXEZLUKN2XAZLSKNSWG4TFOQ", algorithm = TotpAlgorithm.SHA256)),
            item("Tanpa 2FA", pass = "x"),
        )
        val lines = SandiExport.otpauthList(items).split("\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(SandiExport.otpauthList(items).endsWith("\n"))
        val parsed = lines.map { Totp.parseInput(it)!! }
        val byIssuer = parsed.associateBy { it.name }
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP"), byIssuer.getValue("GitHub").config)
        assertEquals("rosyid@example.com", byIssuer.getValue("GitHub").username)
        // Titik dua di penerbit dihapus (pemisah label otpauth); parameter non-standar tetap utuh.
        val bank = byIssuer.getValue("Bank Utama")
        assertEquals(TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 8, 60, TotpAlgorithm.SHA512), bank.config)
        assertEquals("nasabah 01", bank.username)
        assertEquals(TotpAlgorithm.SHA256, byIssuer.getValue("dash.cloudflare.com").config.algorithm)
    }

    // ------------------------------------------------------------------ Google Authenticator

    private class Account(val secret: ByteArray, val name: String, val issuer: String, val algorithm: Long, val digits: Long, val type: Long)
    private class Payload(val accounts: List<Account>, val version: Long, val batchSize: Long, val batchIndex: Long, val batchId: Long)

    /** Dekoder protobuf minimal (independen dari penulis). */
    private class Reader(val b: ByteArray, var pos: Int = 0, val end: Int = b.size) {
        fun varint(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val x = b[pos++].toInt() and 0xff
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }
        fun bytes(): ByteArray { val n = varint().toInt(); return b.copyOfRange(pos, pos + n).also { pos += n } }
    }

    private fun decodeAccount(bytes: ByteArray): Account {
        val r = Reader(bytes)
        var secret = ByteArray(0); var name = ""; var issuer = ""; var alg = 0L; var digits = 0L; var type = 0L
        while (r.pos < r.end) {
            val tag = r.varint().toInt()
            when (tag) {
                0x0a -> secret = r.bytes()
                0x12 -> name = String(r.bytes(), Charsets.UTF_8)
                0x1a -> issuer = String(r.bytes(), Charsets.UTF_8)
                0x20 -> alg = r.varint()
                0x28 -> digits = r.varint()
                0x30 -> type = r.varint()
                else -> error("tag tak dikenal $tag")
            }
        }
        return Account(secret, name, issuer, alg, digits, type)
    }

    private fun decode(uri: String): Payload {
        val u = URI(uri)
        assertEquals("otpauth-migration", u.scheme)
        assertEquals("offline", u.host)
        val data = URLDecoder.decode(u.rawQuery.removePrefix("data="), "UTF-8")
        val r = Reader(Base64.getDecoder().decode(data))
        val accounts = mutableListOf<Account>(); var version = 0L; var size = 0L; var index = -1L; var id = 0L
        while (r.pos < r.end) {
            when (val tag = r.varint().toInt()) {
                0x0a -> accounts += decodeAccount(r.bytes())
                0x10 -> version = r.varint()
                0x18 -> size = r.varint()
                0x20 -> index = r.varint()
                0x28 -> id = r.varint()
                else -> error("tag tak dikenal $tag")
            }
        }
        return Payload(accounts, version, size, index, id)
    }

    @Test fun googleMigrationMatchesHandEncodedProtobuf() {
        val plan = SandiExport.googleMigration(listOf(item("GitHub", "https://github.com", "rosyid@example.com", totp = TotpConfig("JBSWY3DPEHPK3PXP"))), batchId = 7)
        assertEquals(1, plan.codes.size)
        assertEquals(1, plan.accounts)
        val secret = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        val name = "rosyid@example.com".toByteArray()
        val issuer = "GitHub".toByteArray()
        val params = byteArrayOf(0x0a, 10) + secret + byteArrayOf(0x12, 18) + name + byteArrayOf(0x1a, 6) + issuer +
            byteArrayOf(0x20, 1, 0x28, 1, 0x30, 2)
        assertEquals(46, params.size)
        val expected = byteArrayOf(0x0a, 46) + params + byteArrayOf(0x10, 1, 0x18, 1, 0x20, 0, 0x28, 7)
        val data = URLDecoder.decode(plan.codes[0].substringAfter("data="), "UTF-8")
        assertContentEquals(expected, Base64.getDecoder().decode(data))
        // Karakter base64 (+ / =) wajib di-encode di URI.
        assertFalse(plan.codes[0].substringAfter("data=").any { it == '+' || it == '/' || it == '=' })
    }

    @Test fun googleMigrationSplitsIntoNumberedBatchesWithSameId() {
        val items = (1..20).map { n ->
            item("Akun %02d".format(n), user = "user$n@example.com", totp = TotpConfig(Totp.encodeBase32(ByteArray(20) { (it + n).toByte() })))
        }
        val plan = SandiExport.googleMigration(items, batchId = 123_456_789)
        assertEquals(20, plan.accounts)
        val payloads = plan.codes.map(::decode)
        assertEquals(listOf(8, 8, 4), payloads.map { it.accounts.size })
        payloads.forEachIndexed { i, p ->
            assertEquals(1, p.version)
            assertEquals(3, p.batchSize)
            assertEquals(i.toLong(), p.batchIndex)
            assertEquals(123_456_789, p.batchId)
        }
        // Semua akun ada, kuncinya utuh, urut judul.
        val all = payloads.flatMap { it.accounts }
        assertEquals(items.map { it.title }, all.map { it.issuer })
        all.zip(items).forEach { (a, it) ->
            assertContentEquals(Totp.decodeBase32(it.totp!!.secret), a.secret)
            assertEquals(it.username, a.name)
            assertEquals(1, a.algorithm); assertEquals(1, a.digits); assertEquals(2, a.type)
        }
        plan.codes.forEach { assertTrue(it.length < 1_000, "QR terlalu padat: ${it.length}") }
    }

    @Test fun googleMigrationSkipsNonStandardKeysWithReason() {
        val items = listOf(
            item("Standar", user = "a", totp = TotpConfig("JBSWY3DPEHPK3PXP")),
            item("Periode 60", user = "b", totp = TotpConfig("JBSWY3DPEHPK3PXP", period = 60)),
            item("SHA256", user = "c", totp = TotpConfig("JBSWY3DPEHPK3PXP", algorithm = TotpAlgorithm.SHA256)),
            item("Delapan", user = "d", totp = TotpConfig("JBSWY3DPEHPK3PXP", digits = 8)),
            item("Tanpa 2FA", user = "e", pass = "x"),
        )
        val plan = SandiExport.googleMigration(items, batchId = 1)
        assertEquals(1, plan.accounts)
        assertEquals(listOf("Standar"), decode(plan.codes.single()).accounts.map { it.issuer })
        assertEquals(
            listOf("Delapan" to GoogleAuthIssue.Digits, "Periode 60" to GoogleAuthIssue.Period, "SHA256" to GoogleAuthIssue.Algorithm),
            plan.skipped,
        )
    }

    @Test fun googleMigrationLabels() {
        val items = listOf(
            // Judul kosong → host situs sebagai penerbit; nama pengguna kosong → penerbit sebagai nama.
            item("", "https://dash.cloudflare.com", "", totp = TotpConfig("JBSWY3DPEHPK3PXP")),
            item("🔐".repeat(70), user = "x".repeat(80), totp = TotpConfig("JBSWY3DPEHPK3PXP")),
        )
        val accounts = decode(SandiExport.googleMigration(items, batchId = 2).codes.single()).accounts
        val cf = accounts.first { it.issuer == "dash.cloudflare.com" }
        assertEquals("dash.cloudflare.com", cf.name)
        val long = accounts.first { it.issuer != "dash.cloudflare.com" }
        // Dipotong per karakter (emoji tidak terbelah jadi surrogate yatim).
        assertEquals("🔐".repeat(SandiExport.GOOGLE_MAX_LABEL), long.issuer)
        assertEquals("x".repeat(SandiExport.GOOGLE_MAX_LABEL), long.name)
    }

    @Test fun googleMigrationEmptyWhenNothingToMove() {
        val plan = SandiExport.googleMigration(listOf(item("Tanpa 2FA", pass = "x")), batchId = 3)
        assertEquals(emptyList(), plan.codes)
        assertEquals(0, plan.accounts)
    }

    @Test fun fileNames() {
        assertEquals("taskify-sandi-2026-11-19.csv", SandiExport.fileName(ExportKind.GoogleCsv, "2026-11-19"))
        assertEquals("taskify-2fa-2026-11-19.txt", SandiExport.fileName(ExportKind.OtpauthList, "2026-11-19"))
        assertEquals("text/csv", SandiExport.mimeType(ExportKind.GoogleCsv))
    }
}
