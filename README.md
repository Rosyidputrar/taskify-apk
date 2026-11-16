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

### APK rilis bertanda tangan

APK rilis (`versionName` 1.0.0, `versionCode` = nomor run GitHub Actions) dibuat otomatis bila
empat secret ini diisi: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Buat keystore sekali saja dan SIMPAN BAIK-BAIK —
kehilangan keystore = pembaruan APK tidak bisa dipasang menimpa versi lama:

```bash
keytool -genkeypair -keystore taskify-release.jks -alias taskify -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 taskify-release.jks   # isi untuk secret RELEASE_KEYSTORE_BASE64
```

Langkah CI "Sidik jari sertifikat rilis" menulis SHA-256 sertifikat ke ringkasan run; itulah
nilai `sha256_cert_fingerprints` untuk `assetlinks.json` (lihat bagian App Link di bawah,
format huruf besar dipisah titik dua).

CI juga selalu menghasilkan APK debug yang ditandatangani kunci debug tetap
(`app/debug.keystore`, bukan rahasia), sehingga build debug berikutnya bisa dipasang menimpa yang
lama tanpa uninstall. Karena kunci itu publik, APK debug memakai paket terpisah
`com.rosyidputrar.taskify.debug` (nama "Taskify Debug", bisa `debuggable`): ia terpasang
berdampingan dengan versi rilis, tidak bisa menimpanya, dan tidak bisa membaca datanya.
**Untuk pemakaian sehari-hari pasang APK rilis.** APK debug versi lama (sebelum pemisahan paket)
memakai paket `com.rosyidputrar.taskify`; unggah dulu semua perubahan (Pengaturan › Sinkronisasi),
lalu uninstall sebelum memasang APK rilis.

### Unduh APK (GitHub Releases)

Setiap push ke `main` yang lulus tes unit, build rilis bertanda tangan, dan uji asap di emulator
otomatis diterbitkan sebagai **GitHub Release** (`v1.0.0-build.<nomor run>`) berisi satu berkas
`taskify-<versi>-build<N>.apk` — unduh di HP lalu pasang; versi baru menimpa yang lama tanpa
menghapus data. Bisa juga dipicu manual dari tab Actions (Run workflow, branch `main`). Butuh
keempat secret keystore rilis; tanpa itu job rilis gagal dengan pesan yang jelas.

### Uji asap APK rilis

Setiap push, CI membangun APK rilis (R8 full mode, sama persis dengan yang dibagikan) dengan
keystore SEMENTARA — tidak butuh secret keystore — lalu memasangnya di emulator Android 8.0
(API 26, minSdk) dan Android 14 (API 34). `tools/smoke-test.sh` membuka aplikasi lewat launcher,
shortcut (Tangkap/Cari/Kalender), Bagikan teks, ke latar & kembali, dan memutar layar; job gagal
bila proses mati atau ada `FATAL EXCEPTION` (logcat & screenshot terakhir jadi artifact
`smoke-api…`). Bisa juga dijalankan lokal ke emulator/HP yang terhubung:

```bash
tools/smoke-test.sh app/build/outputs/apk/release/app-release.apk
```

## Struktur

```
app/                 Gerbang (konfigurasi → masuk → MFA → database → PIN), shell navigasi,
                     Pengaturan (akun, keamanan, sinkronisasi), koordinator sesi
core/common          Utilitas murni: rupiah, UUIDv7, tanggal WIB, timestamp ISO, batas teks, hash
core/domain          Logika murni teruji JVM: aturan upload, rotasi brankas, gerbang MFA, PIN,
                     RRULE, Markdown, simpan otomatis, insight mood, periode & ringkasan Review, kripto Sandi (PBKDF2/HKDF/AES-GCM,
                     diuji dua arah melawan crypto.ts webapp), TOTP RFC 6238, generator, parser .env/JSON
core/store           Skema PowerSync (identik webapp) + repository Tugas, Tag, Beranda, Kalender,
                     Jurnal, Catatan, Keuangan, Vault, Lampiran, Berkas, Review, Sampah, Tautan, pencarian FTS5
                     — diuji di JVM
                     terhadap PowerSync sungguhan (SQLite + ekstensi PowerSync)
core/data            Database Android, Supabase client, connector upload
core/auth            AuthRepository (login, MFA aal2, ingat 30 hari), kunci PIN (Keystore)
core/designsystem    "Kertas & Tinta": palet, tipografi, kartu indeks, Dok
core/ui              Editor Markdown kertas bergaris + pratinjau native, indikator simpan, tag & tanggal,
                     heatmap aktivitas 26 minggu
core/files           Lampiran: presign Edge Function `files`, unggah R2 via WorkManager (outbox),
                     salinan offline, kompres foto, baca ZIP. Berkas publik: Edge Function `tempfiles`,
                     unggah streaming langsung dari content:// (sampai 1 GB) dengan progres & batal
core/sandi           Sesi brankas (DEK hanya di memori, kunci otomatis), ingat perangkat 30 hari
                     (kunci Android Keystore yang hanya terbuka lewat sidik jari / kunci layar),
                     papan klip yang dibersihkan sendiri, rotasi kunci atomik
core/media           Musik (Worker music-taskify + Media3: putar di latar, notifikasi, layar kunci) dan
                     TikTok (Worker ttdl-taskify dengan header Origin); simpan unduhan ke Download/Taskify
core/reminders       Pengingat tugas sebagai alarm lokal + notifikasi (Selesai / Tunda 10 mnt)
feature/auth         Layar Masuk, verifikasi dua langkah (kode & daftar QR), kunci PIN
feature/tasks        Tugas: quick add bahasa natural, subtask, prioritas, tag, daftar, RRULE, pengingat
feature/calendar     Kalender: grid bulan + strip minggu, agenda gabungan semua modul, kejadian virtual
feature/journal      Jurnal: rak entri, editor + penggeser mood, insight (grafik, sebaran, streak), templat
feature/notes        Catatan: grid staggered, catatan bebas / ditempel ke tanggal, sematkan, Sampah
feature/attachments  Bagian lampiran (foto/file) di Tugas, Jurnal, dan Transaksi
feature/vault        Vault Kode: snippet ber-template, project dengan versi ZIP, penampil isi ZIP,
                     berkas konfigurasi .env/JSON terenkripsi berversi (nilai tersamar, banding, pulihkan)
feature/files        Berkas: kuota 1 GB, unggah dengan masa berlaku, tautan publik (salin/bagikan,
                     privat, cabut & buat baru), ganti nama, unduh, hapus; halaman unduh publik
                     `/unduh/<token>` sebagai App Link tanpa login
feature/review       Review mingguan/bulanan: ringkasan kalimat, statistik + delta vs periode lalu,
                     grafik per hari dengan mood, kategori pengeluaran, sorotan, heatmap, tulis refleksi
feature/links        Bagian "Tertaut" (tautan silang dua arah) di Tugas, Jurnal, Catatan, Transaksi
feature/organize     Halaman Tag (ganti nama/warna, hapus, item per tag) dan Sampah lintas modul
                     (pulihkan, hapus permanen, kosongkan, bersih otomatis 30 hari setelah sinkron)
feature/media        Musik (cari, antrean, acak/ulang, lanjut otomatis, lirik, bilah mini) dan TikTok
                     (ambil link, carousel foto, unduh video/foto/audio)
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
   Selama PIN aktif, pratinjau Taskify di layar Terkini dikosongkan.
6. Keluar akun membersihkan SEMUA sisa di HP: database, lampiran, Sandi, unggahan Berkas, notifikasi
   yang tampil, kotak masuk Bagikan, PIN, dan widget — tiap langkah berdiri sendiri, sesi selalu keluar.

### Widget, shortcut & aksesibilitas

- Widget layar utama **Hari ini**: tugas jatuh tempo hari ini & terlambat dengan centang langsung,
  plus tombol Tangkap. Bila kunci PIN aktif, widget hanya menampilkan jumlah tugas (judul tetap di
  balik PIN). Diperbarui otomatis saat tugas berubah, termasuk dari sinkron.
- Tekan lama ikon: Tangkap, Cari, Kalender, dan modul yang paling sering dibuka (shortcut dinamis).
- Aksesibilitas diuji otomatis (`AccessibilityTest`): setiap elemen yang bisa diketuk wajib punya
  nama untuk TalkBack, dan layar utama direkam pada ukuran huruf 160% (`a11y_font160_*.png`).
  Label tab, angka uang, dan ubin statistik mengecil sendiri alih-alih terpotong.

### Pengaturan

Akun & nama tampilan, kunci PIN, verifikasi dua langkah (tambah authenticator cadangan, hapus —
yang terakhir tidak bisa dihapus karena MFA wajib — dan lupakan perangkat ini), notifikasi
pengingat, sinkronisasi & penyimpanan (perubahan yang ditolak server diumumkan lewat snackbar &
kartu di Beranda, bisa dicoba lagi per item / semua, disalin, atau dibersihkan),
ekspor semua data ke JSON (format sama dengan webapp, termasuk Sampah, jalan offline), tautan ke
Tag & Sampah, tampilan, dok, status konfigurasi server, dan versi.

### Pencarian & Bagikan

- Spotlight (geser dok ke atas / Ctrl+K) mencari ISI tugas, jurnal, catatan, transaksi, snippet,
  dan project lewat indeks SQLite FTS5 lokal (trigger di tabel PowerSync, sama dengan webapp) —
  tetap jalan offline; tag ikut dicari.
- "Bagikan → Taskify" dari aplikasi lain: teks/tautan jadi tugas, jurnal, snippet, atau catat cepat;
  berkas jadi lampiran jurnal, struk transaksi, versi project (ZIP), snippet, atau Berkas publik;
  link TikTok langsung ditawarkan ke pengunduh.

### Tautan unduh publik (App Link)

`PublicFileActivity` menangani `https://<host PUBLIC_WEB_ORIGIN>/unduh/<token>` tanpa melewati
layar masuk/PIN (penerima tautan tidak perlu akun). Agar Android membukanya langsung di aplikasi,
origin web perlu menyajikan `/.well-known/assetlinks.json` berisi package
`com.rosyidputrar.taskify` dan sidik jari SHA-256 sertifikat penanda tangan APK. Tanpa berkas itu,
tautan tetap terbuka di browser (halaman webapp) dan aplikasi bisa dipilih lewat "Buka dengan".

Font: Fraunces, Instrument Sans, JetBrains Mono (SIL Open Font License, lihat `licenses/`).
