package com.rosyidputrar.taskify.core.domain.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test fun onlyContentUrisFromOtherAppsAreAccepted() {
        val pkg = "com.rosyidputrar.taskify"
        val own = setOf("$pkg.files", "$pkg.androidx-startup")
        fun ok(scheme: String?, authority: String?) = Share.isAcceptableSource(scheme, authority, own, pkg)

        // Galeri, file manager, Google Drive.
        assertTrue(ok("content", "com.android.providers.media.documents"))
        assertTrue(ok("content", "media"))
        assertTrue(ok("CONTENT", "com.google.android.apps.docs.storage"))

        // "Dirty Stream": file:// (termasuk berkas privat Taskify) dan provider milik sendiri.
        assertFalse(ok("file", null))
        assertFalse(ok("file", ""))
        assertFalse(ok("content", "$pkg.files"))
        assertFalse(ok("content", "COM.ROSYIDPUTRAR.TASKIFY.FILES"))
        assertFalse(ok("content", "$pkg.androidx-startup"))
        assertFalse(ok("content", "$pkg.provider-baru")) // provider sendiri yang belum terdaftar
        assertFalse(ok("content", "0@$pkg.files")) // authority dengan user id
        assertFalse(ok("content", pkg))
        // Skema & authority aneh.
        assertFalse(ok(null, "media"))
        assertFalse(ok("http", "contoh.id"))
        assertFalse(ok("content", null))
        assertFalse(ok("content", " "))
        // Paket lain yang namanya diawali paket ini tanpa titik bukan milik sendiri.
        assertTrue(ok("content", "${pkg}fake.files"))
    }

    @Test fun debugPackageIsAlsoOwn() {
        val pkg = "com.rosyidputrar.taskify.debug"
        assertFalse(Share.isAcceptableSource("content", "$pkg.files", emptySet(), pkg))
        assertTrue(Share.isAcceptableSource("content", "media", emptySet(), pkg))
    }
}
