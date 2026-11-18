/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.youtubei

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tahap 3: aset youtubei.js terkemas dengan benar di modul (bundle +
 * polyfill ada, berisi API `ArchiveTuneYoutubei` versi 18.0.0).
 *
 * Aset dibaca langsung dari `src/main/assets` (bukan lewat
 * `ApplicationProvider.assets`): unit test modul library di AGP 9 tidak
 * mengekspos aset library ke runtime Robolectric, jadi baca lewat konteks
 * selalu `FileNotFoundException` — bukan bukti aset hilang. Pengemasan
 * aset ke AAR/APK adalah perilaku standar AGP dan diverifikasi terpisah
 * dari APK debug (unzip APK → `assets/youtubei/` harus ada).
 *
 * BUKAN tes preWarm QuickJS: `.so` QuickJS dari AAR memakai bionic
 * Android (`liblog.so`, `libc.so`) sehingga TIDAK bisa dimuat JVM host —
 * `YoutubeiResolver.preWarm()` hanya bisa diverifikasi di perangkat/HP
 * (atau instrumented test). Muat bundle di Node sudah diverifikasi
 * terpisah (lihat `core/youtubei/README.md`): kedua bundle memuat +
 * API identik.
 */
class YoutubeiBundleAssetsTest {

    private fun assetBytes(name: String): String {
        val file = File("src/main/assets/youtubei/$name")
        assertTrue("aset tidak ada: ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    @Test
    fun `bundle youtubei terkemas dan berisi API versi 18_0_0`() {
        val bundle = assetBytes("youtubei.bundle.js")
        assertTrue("bundle terlalu kecil: ${bundle.length}", bundle.length > 1_000_000)
        assertTrue(bundle.contains("ArchiveTuneYoutubei=Object.freeze({version:"))
        assertTrue(bundle.contains("resolvePrepared"))
        assertTrue(bundle.contains("18.0.0"))
    }

    @Test
    fun `runtime polyfills terkemas dan tidak kosong`() {
        val polyfills = assetBytes("runtime-polyfills.js")
        assertTrue("polyfill kosong", polyfills.length > 1_000)
        assertTrue(polyfills.contains("WeakSet"))
    }
}
