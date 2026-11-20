package com.rosyidputrar.taskify.core.domain.sandi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandiImportTest {
    /** Contoh berkas ekspor Pengelola Sandi Google (bentuk & urutan kolom asli, CRLF). */
    private val google = listOf(
        "name,url,username,password,note",
        "github.com,https://github.com/login,rosyid@example.com,Kopi-42!,",
        "\"Bank, Utama\",https://ibank.klikbca.co.id/,rosyid,\"a,b\"\"c\",\"baris 1\nbaris 2\"",
        "com.bca,android://AbCdEf123_-==@com.bca/,rosyidputra,pinjam-dulu-100,",
        "tokopedia.com,https://www.tokopedia.com/,0812,  spasi di tepi  ,",
        "kosong.com,https://kosong.com/,user,,",
        "",
    ).joinToString("\r\n")

    @Test fun readsGoogleExport() {
        val p = SandiImport.read(google)
        assertEquals(ImportFormat.Google, p.format)
        assertEquals(4, p.rows.size)
        assertEquals(1, p.noSecret) // kosong.com tanpa sandi
        assertEquals(0, p.malformed)
        val byTitle = p.rows.associateBy { it.payload.title }
        assertEquals(ItemPayload("github.com", "https://github.com/login", "rosyid@example.com", "Kopi-42!"), byTitle.getValue("github.com").payload)
        val bank = byTitle.getValue("Bank, Utama").payload
        assertEquals("a,b\"c", bank.password)
        assertEquals("baris 1\nbaris 2", bank.notes)
        // Aplikasi Android → format Isi Otomatis Sandi.
        assertEquals("androidapp://com.bca", byTitle.getValue("com.bca").payload.url)
        // Sandi TIDAK dipangkas (spasi bisa bagian dari sandi).
        assertEquals("  spasi di tepi  ", byTitle.getValue("tokopedia.com").payload.password)
        // Nomor baris mengikuti berkas (baris "Bank" memuat baris baru di dalam kutip).
        assertEquals(listOf(2, 3, 5, 6), p.rows.map { it.line })
    }

    @Test fun toleratesBomLfAndOlderHeaderWithoutNote() {
        val p = SandiImport.read("﻿name,url,username,password\ngithub.com,https://github.com,u,p\n")
        assertEquals(ImportFormat.Google, p.format)
        assertEquals(ItemPayload("github.com", "https://github.com", "u", "p"), p.rows.single().payload)
    }

    @Test fun headerIsCaseInsensitiveAndOrderFree() {
        val p = SandiImport.read("Password,Username,URL\nrahasia,budi,https://contoh.id\n")
        assertEquals(ImportFormat.Generic, p.format)
        val item = p.rows.single().payload
        assertEquals("contoh.id", item.title) // judul dari host
        assertEquals("budi", item.username)
        assertEquals("rahasia", item.password)
    }

    @Test fun readsBitwardenWithTotpAndSkipsNonLogins() {
        val csv = listOf(
            "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp",
            ",1,login,GitHub,catatan,,0,https://github.com,rosyid,Kopi-42!,otpauth://totp/GitHub:rosyid?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
            ",,login,Hanya 2FA,,,0,https://cloudflare.com,ops,,JBSWY3DPEHPK3PXP",
            ",,note,Catatan rahasia,isi,,0,,,,",
            ",,login,TOTP rusak,,,0,https://x.com,x,sandi,BUKAN-BASE32-018",
        ).joinToString("\n")
        val p = SandiImport.read(csv)
        assertEquals(ImportFormat.Bitwarden, p.format)
        assertEquals(listOf("GitHub", "Hanya 2FA", "TOTP rusak"), p.rows.map { it.payload.title })
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP"), p.rows[0].payload.totp)
        assertEquals("catatan", p.rows[0].payload.notes)
        assertEquals("", p.rows[1].payload.password) // 2FA saja tetap berguna
        assertNull(p.rows[2].payload.totp)
        assertEquals(1, p.badTotp)
        assertEquals(1, p.noSecret) // catatan Bitwarden
    }

    /** D-14: batas dihitung dari byte — 2 juta karakter CJK = 6 MB byte, ditolak walau jumlah karakternya di bawah 5 juta. */
    @Test fun readBytesEnforcesByteLimitNotCharCount() {
        val header = "name,url,username,password\n"
        val big = header + "x".repeat(100) + "," + "https://a.example.com" + ",u," + "あ".repeat(2_000_000)
        assertFailsWith<ImportException> { SandiImport.readBytes(big.toByteArray(Charsets.UTF_8)) }
        val ok = header + "GitHub,https://github.com,u,p\n" + "あ".repeat(100_000)
        // 100 ribu CJK ≈ 300 KB byte: tetap diterima.
        assertEquals(1, SandiImport.readBytes(ok.toByteArray(Charsets.UTF_8)).rows.size)
    }

    @Test fun rejectsFilesThatAreNotPasswordCsv() {
        assertEquals("Berkas kosong.", assertFailsWith<ImportException> { SandiImport.read("") }.message)
        assertTrue(assertFailsWith<ImportException> { SandiImport.read("Tanggal;Nominal\n2026-11-01;5000") }.message!!.startsWith("Ini bukan berkas sandi"))
        assertTrue(assertFailsWith<ImportException> { SandiImport.read("name,url,username,password\n\"tidak ditutup,https://a.com,u,p") }.message!!.contains("kutip"))
    }

    @Test fun truncatedRowsAreCountedNotCrashing() {
        val p = SandiImport.read("name,url,username,password\nGitHub,https://github.com\nOK,https://ok.com,u,p\n")
        assertEquals(1, p.malformed)
        assertEquals(listOf("OK"), p.rows.map { it.payload.title })
    }

    @Test fun importUrlConvertsOnlyAndroidAppEntries() {
        assertEquals("androidapp://com.bca", SandiImport.importUrl("android://AbC123==@com.bca/"))
        assertEquals("androidapp://id.co.bri.brimo", SandiImport.importUrl("android://x@id.co.bri.brimo"))
        // Hash sertifikat base64 bisa memuat "/" dan "+".
        assertEquals("androidapp://com.bca", SandiImport.importUrl("android://Ab+c/D==@com.bca/"))
        assertEquals("https://github.com/login", SandiImport.importUrl(" https://github.com/login "))
        assertEquals("android://bukan paket", SandiImport.importUrl("android://bukan paket"))
    }

    // ------------------------------------------------------------------ rencana

    @Test fun planNeverOverwritesAndSkipsExactCopies() {
        val existing = listOf(
            ItemPayload("GitHub", "https://github.com", "Rosyid@Example.com", "Kopi-42!"),
            ItemPayload("Tokopedia", "www.tokopedia.com", "0812", "sandi-lama"),
            ItemPayload("BCA", "androidapp://com.bca", "rosyidputra", "pinjam-dulu-100"),
        )
        val parse = SandiImport.read(google + "github.com,https://github.com/login,rosyid@example.com,Kopi-42!,\r\n")
        val plan = SandiImport.plan(parse, existing)
        // github (huruf besar/kecil email & path beda) + BCA (aplikasi) sudah ada persis.
        assertEquals(2, plan.identical)
        // Baris github yang ditambahkan lagi di akhir berkas = kembar di dalam berkas.
        assertEquals(1, plan.duplicateInFile)
        // Tokopedia: akun sama, sandi beda → tetap ditambahkan (item lama tidak disentuh).
        assertEquals(1, plan.differentPassword)
        assertEquals(listOf("Bank, Utama", "tokopedia.com"), plan.add.map { it.title })
    }

    @Test fun exportThenImportRoundTrips() {
        val items = listOf(
            ItemPayload("GitHub", "https://github.com/login", "rosyid@example.com", "Kopi-42!", notes = "baris 1\nbaris 2"),
            ItemPayload("Bank, Utama", "https://jago.com", "rosyid \"rp\"", "a,b\"c\nd", notes = ""),
            ItemPayload("Wi-Fi Rumah", "", "", " RumahKita2026 "),
            ItemPayload("BCA", "androidapp://com.bca", "rosyidputra", "=SUM(A1)"),
        )
        val back = SandiImport.read(SandiExport.passwordsCsv(items)).rows.map { it.payload }
        assertEquals(items.sortedBy { it.title.lowercase() }, back)
        // Mengimpor ulang ke brankas yang sama tidak menambah apa pun.
        assertEquals(0, SandiImport.plan(SandiImport.read(SandiExport.passwordsCsv(items)), items).add.size)
    }

    @Test fun serverSizeLimitIsReachedOnlyByVeryLongNotes() {
        val dek = Dek(ByteArray(32) { it.toByte() })
        fun size(n: Int) = SandiCrypto.encryptItem(dek, "u", "i", ItemPayload("GitHub", "https://github.com", "u", "p", notes = "x".repeat(n)), SandiCrypto.keyRef("d3JhcA==")).length
        assertTrue(size(4_000) <= SandiCrypto.MAX_ITEM_ENC)
        assertTrue(size(7_000) > SandiCrypto.MAX_ITEM_ENC)
    }

    @Test fun limits() {
        val many = "name,url,username,password\n" + (1..SandiImport.MAX_ROWS + 1).joinToString("\n") { "a$it,https://a$it.com,u,p" }
        assertTrue(assertFailsWith<ImportException> { SandiImport.read(many) }.message!!.startsWith("Terlalu banyak baris"))
        assertTrue(assertFailsWith<ImportException> { SandiImport.read("x".repeat(SandiImport.MAX_BYTES + 1)) }.message!!.startsWith("Berkas terlalu besar"))
    }
}
