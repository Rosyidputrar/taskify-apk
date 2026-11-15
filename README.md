# Taskify Android

Versi Android native dari Taskify — Kotlin + Jetpack Compose, offline dulu, tersinkron dengan
webapp lewat Supabase + PowerSync. Rencana lengkap: [docs/PLAN.md](docs/PLAN.md).

## Build

Butuh JDK 21 dan Android SDK (platform 37).

```bash
./gradlew :app:assembleDebug      # APK di app/build/outputs/apk/debug/
./gradlew test                    # semua tes unit
./gradlew :app:recordRoborazziDebug   # rekam ulang tangkapan layar di app/screenshots/
```

### Konfigurasi server

Dibaca saat build dari environment variable (CI: GitHub Secrets) atau `local.properties`
(lokal, tidak di-commit). Tidak ada nilai yang disimpan di repo.

| Nama | Wajib | Fungsi |
| --- | --- | --- |
| `SUPABASE_URL` | ya | Auth & database |
| `SUPABASE_PUBLISHABLE_KEY` | ya | Kunci publik Supabase |
| `POWERSYNC_URL` | ya | Sinkronisasi offline |
| `MUSIC_API_URL` | tidak | Pemutar musik (kosong = disembunyikan) |
| `TIKTOK_API_URL` | tidak | Unduh TikTok (kosong = disembunyikan) |
| `PUBLIC_WEB_ORIGIN` | tidak | Tautan publik Berkas & header `Origin` ke Worker |

APK rilis bertanda tangan dibuat otomatis bila secret `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, dan `RELEASE_KEY_PASSWORD` diisi. Tanpa itu CI
tetap menghasilkan APK debug yang ditandatangani kunci debug tetap (`app/debug.keystore`, bukan
rahasia), sehingga build berikutnya bisa dipasang menimpa yang lama tanpa uninstall.

## Struktur

```
app/                 Gerbang (konfigurasi → masuk → MFA → database → PIN), shell navigasi,
                     Pengaturan (akun, keamanan, sinkronisasi), koordinator sesi
core/common          Utilitas murni: rupiah, UUIDv7, tanggal WIB, timestamp ISO, batas teks, hash
core/domain          Logika murni teruji JVM: aturan upload, rotasi brankas, gerbang MFA, PIN,
                     RRULE, Markdown, simpan otomatis, insight mood, kripto Sandi (PBKDF2/HKDF/AES-GCM,
                     diuji dua arah melawan crypto.ts webapp), TOTP RFC 6238, generator, parser .env/JSON
core/store           Skema PowerSync (identik webapp) + repository Tugas, Tag, Beranda, Kalender,
                     Jurnal, Catatan, Keuangan, Vault, Lampiran — diuji di JVM
                     terhadap PowerSync sungguhan (SQLite + ekstensi PowerSync)
core/data            Database Android, Supabase client, connector upload
core/auth            AuthRepository (login, MFA aal2, ingat 30 hari), kunci PIN (Keystore)
core/designsystem    "Kertas & Tinta": palet, tipografi, kartu indeks, Dok
core/ui              Editor Markdown kertas bergaris + pratinjau native, indikator simpan, tag & tanggal
core/files           Lampiran: presign Edge Function `files`, unggah R2 via WorkManager (outbox),
                     salinan offline, kompres foto, baca ZIP
core/sandi           Sesi brankas (DEK hanya di memori, kunci otomatis), ingat perangkat 30 hari
                     (Android Keystore), papan klip yang dibersihkan sendiri, rotasi kunci atomik
core/reminders       Pengingat tugas sebagai alarm lokal + notifikasi (Selesai / Tunda 10 mnt)
feature/auth         Layar Masuk, verifikasi dua langkah (kode & daftar QR), kunci PIN
feature/tasks        Tugas: quick add bahasa natural, subtask, prioritas, tag, daftar, RRULE, pengingat
feature/calendar     Kalender: grid bulan + strip minggu, agenda gabungan semua modul, kejadian virtual
feature/journal      Jurnal: rak entri, editor + penggeser mood, insight (grafik, sebaran, streak), templat
feature/notes        Catatan: grid staggered, catatan bebas / ditempel ke tanggal, sematkan, Sampah
feature/attachments  Bagian lampiran (foto/file) di Tugas, Jurnal, dan Transaksi
feature/vault        Vault Kode: snippet ber-template, project dengan versi ZIP, penampil isi ZIP,
                     berkas konfigurasi .env/JSON terenkripsi berversi (nilai tersamar, banding, pulihkan)
feature/sandi        Sandi: buat brankas + kunci pemulihan, buka/pulihkan, daftar sandi, kode 2FA dengan
                     cincin hitung mundur + riwayat, cek kunci cepat, generator, ganti master password
feature/finance      Keuangan: saldo & karusel dompet, 6 tab (Ringkasan, Transaksi, Anggaran, Berulang,
                     Hutang, Kelola), editor transaksi, grafik, ekspor CSV, transaksi berulang otomatis
build-logic/         Convention plugin Gradle
tools/sandi-vectors  Skrip Node untuk membangkitkan & memverifikasi vektor uji kripto Sandi
```

### Alur saat aplikasi dibuka

1. Konfigurasi server kosong → layar penjelasan (bukan crash).
2. Belum masuk → layar Masuk (sapaan memakai nama tersimpan, bisa dimatikan).
3. Sesi belum aal2 → verifikasi dua langkah: kode authenticator, atau daftar authenticator baru
   (QR + tombol buka langsung di aplikasi authenticator). "Ingat perangkat ini 30 hari".
4. Database lokal disiapkan: bila akun berbeda dari pemilik sebelumnya, isinya dikosongkan dulu,
   lalu sinkronisasi PowerSync dimulai.
5. PIN aktif → layar kunci (5 percobaan → jeda 30 detik; Lupa PIN = verifikasi password akun).

Font: Fraunces, Instrument Sans, JetBrains Mono (SIL Open Font License, lihat `licenses/`).
