package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Identitas visual tiap modul: label, ikon, dan satu warna datar "penanda indeks".
 * [light]/[dark] dipakai sebagai teks/ikon di atas permukaan kertas/tinta (≥ 4.5:1 di permukaan
 * utama) dan sebagai isi tab dengan teks [onLight]/[onDark].
 */
enum class TaskifyModule(
    val label: String,
    val tagline: String,
    val icon: ImageVector,
    val light: Color,
    val dark: Color,
) {
    Home("Hari Ini", "Ringkasan, fokus, aktivitas", Icons.Outlined.WbTwilight, Color(0xFF1D5867), Color(0xFF8FD0DF)),
    Tasks("Tugas", "Quick add, subtask, berulang", Icons.Outlined.ChecklistRtl, Color(0xFFA8341A), Color(0xFFFF9A7A)),
    Calendar("Kalender", "Pusat semua aktivitas", Icons.Outlined.CalendarMonth, Color(0xFF1B6375), Color(0xFF7FC8DA)),
    Journal("Jurnal", "Tulis harian & mood", Icons.Outlined.AutoStories, Color(0xFF7A3F87), Color(0xFFD4A2E0)),
    Notes("Catatan", "Harian & bebas", Icons.AutoMirrored.Outlined.StickyNote2, Color(0xFF735400), Color(0xFFE5C15C)),
    Finance("Keuangan", "Dompet, anggaran, hutang", Icons.Outlined.Payments, Color(0xFF2A603C), Color(0xFF8FD4A4)),
    Vault("Vault Kode", "Snippet, project, konfigurasi", Icons.Outlined.Code, Color(0xFF3F55A0), Color(0xFFA9B8F0)),
    Sandi("Sandi", "Brankas terenkripsi & TOTP", Icons.Outlined.Key, Color(0xFF8B2635), Color(0xFFF2A0AA)),
    Files("Berkas", "Tautan unduh sementara", Icons.Outlined.FolderZip, Color(0xFF7A4E23), Color(0xFFE3B07C)),
    Review("Review", "Mingguan, bulanan, heatmap", Icons.Outlined.Insights, Color(0xFF566318), Color(0xFFC5D27A)),
    Settings("Pengaturan", "Tema, keamanan, sinkron", Icons.Outlined.Tune, Color(0xFF4F555C), Color(0xFFB9C0C8)),
    Tags("Tag", "Semua item per tag", Icons.Outlined.LocalOffer, Color(0xFF6C4BA0), Color(0xFFC3AEEB)),
    Trash("Sampah", "Pulihkan yang terhapus", Icons.Outlined.DeleteSweep, Color(0xFF5F5750), Color(0xFFC8BEB3)),
    Music("Musik", "Cari lagu, antrean, lirik", Icons.Outlined.MusicNote, Color(0xFF9A3456), Color(0xFFF3A0BC)),
    TikTok("TikTok", "Lihat & unduh konten", Icons.Outlined.OndemandVideo, Color(0xFF1F1C17), Color(0xFFE8E8E8)),
    Notifications("Notifikasi", "Semua yang masuk hari ini", Icons.Outlined.NotificationsActive, Color(0xFF8C4A00), Color(0xFFFFB86B)),
    Wellbeing("Kesehatan Digital", "Waktu layar & kebiasaan HP", Icons.Outlined.Timelapse, Color(0xFF00639B), Color(0xFF94CCFF)),
    ;

    companion object {
        val onLight = Color.White
        val onDark = Color(0xFF141414)

        /** Modul utama, urutan tampil di Ruang. */
        val main = listOf(Home, Tasks, Calendar, Journal, Notes, Finance, Vault, Sandi, Files, Review, Notifications, Wellbeing, Settings)

        /** Fitur lintas modul yang juga punya halaman sendiri. */
        val extras = listOf(Tags, Trash, Music, TikTok)
    }
}
