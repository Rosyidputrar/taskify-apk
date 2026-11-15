package com.rosyidputrar.taskify.ui.placeholder

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule

/** Isi tiap modul sesuai spesifikasi — ditampilkan selama layarnya masih dibangun. */
internal val ModuleRoadmap: Map<TaskifyModule, List<String>> = mapOf(
    TaskifyModule.Home to listOf("Ringkasan hari ini", "Kartu fokus", "Aktivitas hari ini"),
    TaskifyModule.Tasks to listOf(
        "Quick add bahasa natural: \"besok jam 9 #kerja\"",
        "Subtask dengan progres, prioritas, tag",
        "Filter aktif & selesai, detail tugas",
        "Tugas berulang (RRULE)",
        "Pengingat dengan notifikasi",
        "Urungkan lewat snackbar",
    ),
    TaskifyModule.Calendar to listOf(
        "Tampilan bulanan yang menciut jadi strip minggu",
        "Agenda per hari: tugas, jurnal, catatan, transaksi, vault",
        "Tugas berulang sebagai kejadian virtual ↻",
    ),
    TaskifyModule.Journal to listOf(
        "Editor harian dengan simpan otomatis",
        "Mood skala 1–5, grafik mood & insight",
        "Cari & filter berdasarkan mood",
        "Templat refleksi mingguan & bulanan",
    ),
    TaskifyModule.Notes to listOf("Catatan harian & bebas", "Overview grid", "Editor Markdown", "Pencarian"),
    TaskifyModule.Finance to listOf(
        "Tab Ringkasan, Transaksi, Anggaran, Berulang, Hutang, Kelola",
        "Multi dompet & kategori berikon",
        "Grafik, input rupiah, anggaran bulanan",
        "Transaksi berulang",
    ),
    TaskifyModule.Vault to listOf(
        "Snippet kode dengan bahasa & salin cepat",
        "Project: arsip ZIP, viewer, unggah versi baru",
        "Konfigurasi .env terenkripsi",
    ),
    TaskifyModule.Sandi to listOf(
        "Brankas dengan master password",
        "Generator sandi & pengukur kekuatan",
        "TOTP dengan kode & riwayat",
        "Kunci pemulihan & file recovery",
        "Rotasi kunci brankas & device trust",
    ),
    TaskifyModule.Files to listOf(
        "Unggah berkas sementara ke R2",
        "Pilihan masa kedaluwarsa",
        "Tautan publik bertoken & halaman unduh",
    ),
    TaskifyModule.Review to listOf(
        "Ringkasan mingguan & bulanan",
        "Heatmap aktivitas 26 minggu",
        "Statistik per modul",
        "Tulis refleksi → Jurnal dengan templat",
    ),
    TaskifyModule.Settings to listOf(
        "Kunci aplikasi PIN & verifikasi dua langkah",
        "Notifikasi pengingat",
        "Status sinkronisasi & penyimpanan",
        "Ekspor data, hapus data perangkat, info akun",
        "Daftar error upload yang ditolak server",
    ),
    TaskifyModule.Tags to listOf("Halaman per tag lintas modul", "Tag picker di tugas"),
    TaskifyModule.Trash to listOf("Soft delete di semua modul", "Pulihkan atau hapus permanen", "Bersih otomatis 30 hari"),
    TaskifyModule.Music to listOf("Cari lagu", "Antrean", "Lirik", "Main di latar dengan kontrol notifikasi"),
    TaskifyModule.TikTok to listOf("Tempel atau bagikan tautan", "Pratinjau video & foto", "Unduh ke galeri"),
)
