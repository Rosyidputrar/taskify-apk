package com.rosyidputrar.taskify.ui.shell

import com.rosyidputrar.taskify.core.store.NotSignedInException
import com.rosyidputrar.taskify.session.LogoutIncompleteException

/**
 * Teks untuk error yang lolos ke [com.rosyidputrar.taskify.core.designsystem.component.UiErrors].
 * Pesan teknis (SQL, stack) tidak ditampilkan; detailnya tetap masuk log.
 */
internal fun userMessageFor(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        chain.any { it is NotSignedInException } -> "Belum masuk akun — perubahan tidak bisa disimpan. Masuk dulu lalu coba lagi."
        chain.any { it is LogoutIncompleteException } -> "Sudah keluar, tetapi sebagian data lokal gagal dihapus. Data itu dibuang otomatis saat akun lain masuk."
        chain.any { it is java.io.IOException || it.javaClass.name.startsWith("io.ktor.") } -> "Gagal terhubung ke server. Periksa internet lalu coba lagi."
        chain.any { it.javaClass.name.contains("SQLite", ignoreCase = true) || it.javaClass.name.startsWith("com.powersync.") } ->
            "Gagal menyimpan di perangkat. Coba lagi; bila terus terjadi, mulai ulang aplikasi."
        else -> "Terjadi kesalahan. Coba lagi."
    }
}
