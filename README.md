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
core/media           Musik (Worker music-taskify + Media3: putar di latar, notifikasi, layar kunci; now-playing
                     satu panggilan, fallback otomatis opus → konverter MP3, seek hanya untuk direct/R2,
                     URL kedaluwarsa diminta ulang, arsip R2, SponsorBlock, volume rata, pewaktu tidur,
                     antrean tersimpan lintas proses, sesi 401 di-refresh sekali) dan TikTok (Worker
                     ttdl-taskify dengan header Origin); simpan unduhan ke Download/Taskify
core/ai              Klien AI: OpenAI-compatible (OpenAI, OpenRouter, Groq, Gemini, DeepSeek, Mistral, …)
                     & Anthropic, streaming SSE + pemanggilan alat, ambil daftar model, kunci API di Keystore
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
feature/media        Musik: Jelajah (beranda, tangga lagu Indonesia, rilis baru, mood & genre, radio
                     langsung), pencarian lengkap (hasil teratas, lagu, album, artis, playlist, podcast,
                     episode + saran ketik), halaman album/playlist/artis/podcast, link YouTube ditempel
                     atau dibagikan, mix radio, lirik bergulir, pengaturan kualitas; "Musik fokus" di Mode
                     Fokus. TikTok (ambil link, carousel foto, unduh video/foto/audio)
feature/sandi        Sandi: buat brankas + kunci pemulihan, buka/pulihkan, daftar sandi, kode 2FA dengan
                     cincin hitung mundur + riwayat, cek kunci cepat, generator, ganti master password
feature/finance      Keuangan: saldo & karusel dompet, 6 tab (Ringkasan, Transaksi, Anggaran, Berulang,
                     Hutang, Kelola), editor transaksi, grafik, ekspor CSV, transaksi berulang otomatis
feature/ai           Asisten AI: sesi chat lokal, streaming halus (per kalimat, fade-in), Markdown lengkap +
                     kode berwarna, alat baca & tulis data Taskify dengan konfirmasi
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

### Tema

Pengaturan › Tampilan: mode Terang/Gelap/Ikuti sistem, **palet warna**, dan **kontras** (Standar/Sedang/Tinggi).
- Palet siap pakai: Kertas & Tinta (desain asli), Laut, Hutan, Senja, Lavender, Sakura, Grafit, dan
  Material You (warna wallpaper, Android 12+).
- **Kustom** lewat Studio tema: warna dasar (contoh cepat, hue + kekuatan warna, atau hex), gaya palet
  (Seimbang, Cerah, Ekspresif, Kalem, Setia warna, Monokrom), dan "nuansa kertas" (permukaan krem/arang
  khas Taskify). Pratinjau terang & gelap langsung, plus strip palet tonal.
- Semua peran warna diturunkan dari satu warna dasar lewat ruang warna HCT (port material-color-utilities);
  warna modul diselaraskan ke warna utama dengan tone terkunci. ThemeEngineTest memeriksa setiap palet ×
  kontras × terang/gelap dan ratusan tema kustom dari warna ekstrem: teks/latar utama & warna modul ≥ 4.5:1.

### Mode Fokus & Beranda

- **Mode Fokus (Pomodoro)** — dari tombol *Mulai fokus* di kartu Fokus Beranda, tombol *Fokus* di
  detail tugas, atau tile Quick Settings *Fokus*. Fokus 15/25/45/60 menit → istirahat 5 menit,
  istirahat panjang 15 menit tiap 4 putaran. Checklist subtask tampil di layar fokus; layar tetap
  menyala selama hitung mundur.
- Tanpa foreground service: notifikasi berjalan memakai hitung mundur bawaan sistem dan akhir fase
  dibangunkan AlarmManager (Jeda/Lanjut, Lewati, Berhenti langsung dari notifikasi). Status disimpan
  sebagai jam dinding, jadi tetap benar walau aplikasi ditutup paksa.
- Opsional **Jangan Ganggu** selama fase fokus (perlu akses di Setelan sistem). Hanya dimatikan lagi
  bila Taskify yang menyalakannya.
- Riwayat fokus disimpan di tabel lokal `focus_sessions` (tidak disinkronkan; ikut terhapus saat
  Keluar) dan tampil di Review sebagai *Waktu fokus*.
- **Susun Beranda** (ikon di samping sapaan): urutkan atau sembunyikan pita minggu, Fokus, Ringkasan,
  dan Aktivitas; pilih ubin ringkasan (termasuk *Saldo semua dompet* dan *Masuk bulan ini*). Pita
  minggu menampilkan titik tugas/jurnal/catatan/transaksi per hari.
- **Pintasan di Hari ini**: baris kartu yang langsung membuka *isi* modul, bukan halaman depannya.
  - *Disematkan* (maks. 12): catatan, jurnal, snippet, project, konfigurasi, chat asisten, daftar
    tugas, dompet, tag, dan *tampilan cepat* (Tugas terlambat, Kode 2FA, Anggaran, Tulis jurnal, …).
    Sematkan lewat kartu "Tambah pintasan" (ada pencarian), menu ⋮ di layar isi (*Sematkan ke
    Hari ini*), atau tekan lama kartu "Lanjutkan".
  - *Lanjutkan* otomatis: isi yang terakhir dibuka di HP ini atau diubah, bisa dimatikan.
  - Tekan lama kartu → lepas / geser kiri-kanan; semuanya juga bisa diatur di Susun Beranda.
  - Disimpan di tabel lokal `home_pins` & `home_recent` (tidak disinkronkan; ikut terhapus saat Keluar).
  - Ubin ringkasan membuka tampilan yang relevan (mis. "2 terlambat" → filter Terlambat).

### Isi otomatis Sandi

Sandi bisa dijadikan layanan Isi Otomatis Android (Sandi → Pengaturan brankas → *Jadikan Sandi
layanan isi otomatis*).

- **Cocok berdasarkan alamat**: di browser dicocokkan dengan domain situs (persis, atau domain
  induk yang sama: `accounts.google.com` ↔ `google.com`); di aplikasi dengan paket
  (`androidapp://com.bca`) atau domain terbalik (`tokopedia.com` ↔ `com.tokopedia.tkpd`). Domain
  mirip seperti `google.com.evil.io` tidak dianggap cocok. Item lain bisa dipilih manual lewat
  *Cari di Sandi…*, dengan peringatan "Alamat tidak cocok" lebih dulu.
- **Brankas terkunci**: hanya baris *Buka Sandi* yang ditawarkan, tanpa isi brankas. Brankas dibuka
  dengan sidik jari / kunci layar (bila perangkat diingat) atau master password, lalu pilih item.
  Brankas yang sudah lewat batas kunci otomatis dikunci, bukan dipakai, walau Taskify di latar.
- **Simpan sandi baru**: setelah masuk di aplikasi lain, sistem menawarkan *Simpan ke Taskify
  Sandi*. Item dengan nama pengguna sama diperbarui; selain itu dibuat item baru. Saat terkunci,
  kredensial hanya dititipkan di memori proses sampai brankas dibuka.
- Jendela isi otomatis memakai `FLAG_SECURE` dan tidak muncul di Recents.

### Notifikasi & Kesehatan Digital

Dua modul yang membaca data perangkat. Datanya **hanya di HP ini** (tabel lokal, tidak pernah
disinkronkan), terkunci di balik PIN aplikasi (kunci tampilan, bukan enkripsi data), dan terhapus saat Keluar.

**Notifikasi** (izin *Akses notifikasi*; APK dari GitHub di Android 13+ perlu *Izinkan setelan terbatas* dulu)
- Lini waktu per jam, ringkasan + grafik 24 jam, pita hari sesuai masa simpan (1–30 hari), saringan
  kategori & "Perlu ditindak", per aplikasi, pencarian (juga di Spotlight).
- Percakapan beruntun digabung; geser kanan = jadikan tugas (tenggat dibaca dari teks), geser kiri =
  sembunyikan; detail: buka di aplikasi, jadikan tugas/catatan, catat transaksi, salin, kecualikan.
- **Draf transaksi** dari notifikasi bank & e-wallet (BCA, Livin', BRImo, BNI, BSI, Jago, SeaBank, blu,
  GoPay, OVO, DANA, ShopeePay, LinkAja, …): nominal, masuk/keluar, merchant, tebakan kategori; dompet
  diingat per aplikasi. Tidak pernah tersimpan otomatis — juga muncul sebagai kartu di Keuangan.
- **Paket**: status terakhir per resi dari marketplace & kurir.
- Kode OTP disensor; tingkat penyimpanan Lengkap / Judul saja / Hitungan saja; notifikasi berjalan
  diabaikan; spanduk + tips Autostart bila pencatat dimatikan sistem.

**Kesehatan Digital** (izin *Akses penggunaan*; data ±7 hari terakhir langsung diimpor)
- Waktu layar per hari & per aplikasi, berapa kali dibuka, buka kunci (+ jeda rata-rata), jam pertama
  HP dipegang (vs biasanya), jumlah notifikasi.
- *Sepanjang hari*: pita 24 jam aplikasi yang dipakai + titik notifikasi; ketuk jam untuk rinciannya.
- Mode Minggu: batang bertumpuk per aplikasi + rata-rata; detail aplikasi: per jam, riwayat 7/30 hari,
  daftar sesi. Jam pergantian hari bisa diatur (mis. 04.00); aplikasi bisa dikecualikan.
- Disalin dari sistem tiap jam (WorkManager). Ubin di Beranda (Susun Beranda) & bagian di Review.

### Catatan, Jurnal & Snippet: baca, bagikan, salin

- Catatan, entri jurnal, dan snippet yang sudah ada **dibuka di mode Baca/Lihat**; tombol mengambang
  **Ubah** untuk menulis. Item baru/kosong langsung di mode Tulis. Teks di mode Baca bisa diblok.
- **Bagikan**: kirim sebagai teks, atau berkas Markdown (.md), teks biasa (.txt), halaman web (.html),
  PDF A4 bertingkat halaman, dan **gambar PNG** (lembar kertas setinggi isi; isi sangat panjang dipotong
  rapi dengan saran memakai PDF). Jurnal menyertakan tanggal & mood, catatan bertanggal menyertakan tanggalnya.
- **Salin**: teks (tanpa simbol Markdown), Markdown, dan **gambar** — bisa ditempel di chat & dokumen.
- **Snippet**: bagikan sebagai berkas kode dengan ekstensi sesuai bahasa (.kt, .py, .sql, …), Markdown
  berpagar, atau **gambar kode** (jendela gelap, nomor baris, pewarnaan sintaks). Kode yang tampak berisi
  rahasia (token, password, API key) meminta konfirmasi sebelum dibagikan; salinannya ditandai sensitif.
- **Geser ke kiri** pada kartu catatan, jurnal, atau snippet → pindah ke Sampah, dengan **Urungkan**.
- Berkas bagikan hanya disimpan sementara di cache aplikasi (FileProvider), tanpa izin penyimpanan.

### Asisten AI

Pakai kunci API sendiri (*bring your own key*): pilih penyedia (OpenAI-compatible atau Anthropic),
isi base URL (ada preset), simpan kunci, lalu **Tes koneksi & muat model**.

- Kunci API dienkripsi Android Keystore dan tidak pernah ditampilkan lagi; bila Keystore gagal, kunci
  tidak disimpan sama sekali. Hanya HTTPS.
- Percakapan tersimpan **di HP ini saja** (tidak sinkron) dan terhapus saat Keluar. System prompt per
  percakapan, dengan variabel `{hari}` `{tanggal}` `{jam}` `{zona}`.
- Jawaban mengalir per kalimat/baris dengan fade-in (bukan per huruf), bisa dihentikan, diulang, atau
  pesan terakhir diubah. Markdown GFM lengkap: tabel, checklist, kutipan, tautan, kode berwarna + salin.
- **Akses data** mati secara bawaan dan dinyalakan per percakapan (dengan persetujuan). Saat hidup, asisten
  bisa *membaca* tugas, catatan, jurnal, dan keuangan (cari, daftar, ringkasan, saldo, anggaran) dan
  *membuat* tugas, catatan, atau transaksi — setiap tulisan tampil sebagai kartu yang harus kamu setujui
  dulu, dengan dompet/kategori/daftar yang sudah dicocokkan. Sandi, Brankas kode, dan Berkas tidak pernah
  bisa diakses. Isi pesan dan data yang dibaca alat dikirim ke penyedia yang kamu pilih.

### Pengaturan

Akun & nama tampilan, kunci PIN, verifikasi dua langkah (tambah authenticator cadangan, hapus —
yang terakhir tidak bisa dihapus karena MFA wajib — dan lupakan perangkat ini), notifikasi
pengingat, sinkronisasi & penyimpanan (perubahan yang ditolak server diumumkan lewat snackbar &
kartu di Beranda, bisa dicoba lagi per item / semua, disalin, atau dibersihkan),
ekspor semua data ke JSON (format sama dengan webapp, termasuk Sampah, jalan offline), tautan ke
Tag & Sampah, tampilan, dok, status konfigurasi server, dan versi.

### Pencarian & Bagikan

- Spotlight (tarik dok ke atas — panelnya ikut tertarik mengikuti jari; atau Ctrl+K) mencari ISI tugas, jurnal, catatan, transaksi, snippet,
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
