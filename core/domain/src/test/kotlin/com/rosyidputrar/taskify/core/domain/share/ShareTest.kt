package com.rosyidputrar.taskify.core.domain.share

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareTest {
    @Test fun kinds() {
        assertEquals(SharedKind.Zip, Share.kind("repo.ZIP", "application/octet-stream"))
        assertEquals(SharedKind.Image, Share.kind("struk.jpg", "image/jpeg"))
        assertEquals(SharedKind.Image, Share.kind("invoice.pdf", "application/pdf"))
        assertEquals(SharedKind.Text, Share.kind("main.kt", "application/octet-stream"))
        assertEquals(SharedKind.Text, Share.kind("catatan", "text/plain"))
        assertEquals(SharedKind.Other, Share.kind("lagu.mp3", "audio/mpeg"))
    }

    @Test fun mergedText() {
        // Browser: judul halaman + URL; teks berisi URL yang sama → URL tidak diulang
        assertEquals("Artikel bagus\nBaca ini https://x.id/a", Share.text("Artikel bagus", "Baca ini https://x.id/a", "https://x.id/a"))
        assertEquals("https://vt.tiktok.com/abc", Share.text("", "https://vt.tiktok.com/abc", "https://vt.tiktok.com/abc"))
        assertEquals("", Share.text(" ", "", ""))
        assertEquals("Beli susu", Share.firstLine("", "\nBeli susu\nyang rendah lemak"))
        assertEquals("Dibagikan", Share.firstLine("", ""))
    }
}
