# Rencana Taskify Android (APK native)

Versi Android **native** dari Taskify: Kotlin + Jetpack Compose. Semua fitur & subfitur dari
spesifikasi webapp ada di sini, tetapi **UI/UX dirancang ulang dari nol** — tidak meniru layout,
navigasi, maupun gaya visual webapp.

---

## 1. Keputusan utama

| Topik | Keputusan | Alasan |
| --- | --- | --- |
| Bahasa & UI | **Kotlin 2.x + Jetpack Compose + Material 3** (tanpa Flutter) | Satu bahasa, akses penuh API Android (Keystore, AlarmManager, Share intent, MediaSession, DownloadManager) |
| Backend | **Supabase + PowerSync yang sama dengan webapp** | Data HP ↔ laptop tersinkron. Skema, RLS, Sync Streams, dan Edge Function tidak perlu diubah |
| Database lokal | **PowerSync Kotlin SDK** (SQLite) | Offline dulu: semua baca/tulis ke SQLite lokal, antrean upload ke Postgres saat online |
| Auth | **supabase-kt** (Auth + Functions + Postgrest) | MFA TOTP, wajib sesi `aal2`, ingat perangkat 30 hari |
| Kriptografi Sandi | `javax.crypto` + Android Keystore | Format **harus identik** dengan webapp (PBKDF2-SHA256 600rb, AES-256-GCM, AAD, label kunci, padding 256 byte) agar brankas bisa dibuka di HP & laptop |
| Pengingat | **Alarm lokal** (AlarmManager exact + WorkManager) dari `tasks.remind_at` | Jalan offline, tanpa mengubah server. FCM bisa ditambah nanti (butuh ubah `send-reminders`) |
| Min/target SDK | minSdk 26 · target 36 · compile 37 | API kripto & notifikasi modern, mencakup ±97% perangkat |
| Build | Gradle (Kotlin DSL) + version catalog, GitHub Actions menghasilkan APK | Kredensial dari GitHub Secrets / `local.properties`, tidak pernah di-commit |

### Aturan kompatibilitas data (wajib, karena satu database dengan webapp)

- `id` = UUIDv7 dibuat di perangkat; `user_id` di setiap baris; soft delete `deleted_at`.
- Uang = integer rupiah; tanggal tanpa jam = `YYYY-MM-DD`; timestamp = ISO UTC; zona tampilan WIB.
- RRULE RFC 5545 dengan BY* eksplisit, model bergulir (satu baris aktif per `series_id`), aturan
  tanggal 29–31 → akhir bulan (`BYMONTHDAY=28,29,30,31;BYSETPOS=-1`).
- ID transaksi berulang & hutang deterministik (UUID dari `recurring_id + tanggal`, `debt-open`/`debt-settle`).
- Batas panjang teks (`LIMITS`) dipotong di klien sebelum tulis; penolakan permanen server masuk
  tabel lokal `upload_errors`.
- Ciphertext Sandi/Konfigurasi berlabel kunci (`<16 hex>.<base64>`); rotasi kunci hanya lewat RPC
  `apply_vault_rotation`.
- **Bukti kompatibilitas**: vektor uji dibangkitkan dari kode webapp (Node) lalu diuji di JVM —
  APK harus bisa membaca data buatan webapp dan sebaliknya (kripto, quick-parse, RRULE, TOTP).

---

## 2. Bahasa desain: "Kertas & Tinta"

Webapp memakai kaca transparan, gradasi aurora, dan blob warna. APK sengaja kebalikannya:

- **Permukaan solid bertekstur kertas.** Terang: krem hangat seperti buku catatan. Gelap: tinta
  arang kebiruan. Tidak ada blur, tidak ada gradasi latar.
- **Penanda indeks.** Tiap modul punya satu warna datar yang tampil sebagai "tab indeks buku" di
  tepi kartu dan rel lini waktu — bukan gradasi ikon.
- **Tipografi memimpin.** Serif display untuk tanggal besar, angka saldo, dan judul layar; sans
  untuk isi; monospace untuk kode, sandi, dan TOTP.
- **Gerak bermakna, bukan dekorasi.** FAB berubah jadi sheet (pola MaterialSheetFab), kartu
  mengembang jadi detail (shared element), konfeti kecil saat semua tugas hari ini selesai.
- Tema **Terang / Gelap / Sistem**, kontras teks ≥ 4.5:1 di kedua tema.

### Navigasi (berbeda total dari bottom-nav 5 item + sidebar webapp)

```
┌───────────────────────────────────────────────┐
│                  konten layar                 │
│                                               │
│   ╭───────────────────────────────────────╮   │
│   │ ♪ mini player (muncul saat musik main) │   │
│   ╰───────────────────────────────────────╯   │
│   ╭──────────╮   ╭─────╮   ╭──────────────╮   │
│   │ Hari Ini │   │  ✚  │   │ Kalender ▦ │ ☰ │   ← "Dok" mengambang
│   ╰──────────╯   ╰─────╯   ╰──────────────╯   │
└───────────────────────────────────────────────┘
```

- **Dok mengambang** berisi 3 tujuan + tombol tengah:
  - **Hari Ini** (Beranda) · **Kalender** (pusat) · **Ruang** (☰, peluncur semua modul).
  - **✚ Tangkap** di tengah: ketuk = sheet catat cepat; tahan = pilih jenis (tugas/catatan/jurnal/transaksi).
  - **Geser dok ke atas** = Spotlight (pencarian global / command palette). Keyboard fisik:
    **Ctrl/⌘ + K**.
- **Ruang** = layar peluncur berupa grid "kartu indeks" 11 modul + Tag, Sampah, Musik, TikTok.
  Satu modul favorit bisa disematkan ke Dok sebagai slot tambahan (Hari Ini & Kalender tetap).
- **Tablet / Chromebook / lanskap**: Navigation Rail + tata letak **daftar–detail dua panel**
  (adaptive), cocok dipakai bersama keyboard.
- Pintu masuk tambahan: App Shortcuts (tahan ikon launcher), Quick Settings tile "Tangkap",
  dan widget layar utama "Hari Ini" (Glance).

### Referensi pola dari awesome-android-ui

Daftar wasabeef/awesome-android-ui kebanyakan berbasis View lama, jadi dipakai sebagai **pola
interaksi**, lalu dibangun ulang di Compose (atau memakai versi Compose-nya bila ada):

| Pola / library di daftar | Dipakai untuk |
| --- | --- |
| **MaterialSheetFab**, ExpandableBottomBar | FAB Tangkap yang berubah jadi sheet; Dok |
| **CompactCalendarView**, material-calendarview, Android-Week-View | Kalender bulan yang menciut jadi strip minggu; agenda per hari |
| **SwipeToAction**, AndroidSwipeLayout, RecyclerViewSwipeDismiss | Geser tugas: selesai / tunda / hapus |
| **sticky-headers-recyclerview**, StickyListHeaders | Grup "Terlambat · Hari ini · Besok · Nanti", transaksi per tanggal |
| **AndroidStaggeredGrid** | Grid catatan bertinggi bebas |
| **CircleProgress**, **ArcProgressStackView** | Cincin progres subtask, busur anggaran, hitung mundur TOTP |
| **MPAndroidChart / WilliamChart / EazeGraph** | Grafik mood & keuangan (dibuat dengan Vico/Canvas Compose) |
| **Material Code input**, **LolliPin** | Input PIN & kode MFA 6 digit |
| **TextDrawable / RoundedLetterView** | Avatar huruf item Sandi & dompet |
| **AndroidTreeView** | Penjelajah isi ZIP project |
| **CodeView** | Penampil snippet dengan sorotan sintaks |
| **Shimmer (facebook)** | Skeleton saat sinkron pertama |
| **Lottie**, **Konfetti** | Ilustrasi state kosong; perayaan tugas selesai |
| **Balloon (skydoves)** | Tooltip onboarding (mis. sintaks quick add) |
| **Landscapist (skydoves)** | Muat gambar lampiran, sampul lagu, thumbnail TikTok |
| **ColorPickerView** | Warna tag & kategori |
| **Android PDFView** | Pratinjau lampiran PDF (pakai `PdfRenderer` bawaan) |

---

## 3. Rancangan layar per modul

### 1. Beranda — "Hari Ini"
- Header tanggal serif besar + sapaan dengan nama tampilan; strip 7 hari yang bisa digeser.
- **Kartu Fokus "Sekarang"**: satu tugas terpenting, cincin progres subtask, tombol selesai &
  tunda, geser untuk ganti fokus.
- **Ringkasan bento 2×2**: tugas tersisa/terlambat, mood hari ini, pengeluaran hari ini vs
  anggaran, streak jurnal.
- **Aktivitas hari ini**: lini waktu vertikal dengan rel warna modul (tugas, jurnal, catatan,
  transaksi, vault). Heatmap mini 16 minggu di bawah, ketuk → Review.

### 2. Tugas
- **Bar quick add** di atas daftar dengan **parsing bahasa natural langsung**: token yang dikenali
  ("besok", "jam 9", "#kerja", "!tinggi", "tiap senin") langsung berubah jadi chip berwarna saat
  diketik, jadi pengguna melihat hasil sebelum menyimpan.
- Segmen **Aktif / Selesai** + chip filter (prioritas, tag, daftar, tanggal).
- Daftar bergrup dengan header lengket; geser kanan = selesai, kiri = tunda/hapus;
  **Urungkan** lewat snackbar untuk semua aksi.
- **Detail tugas** = sheet setinggi layar: judul, catatan, tanggal & jam, prioritas, tag picker,
  **subtask dengan progres**, pengingat, lampiran, tautan, riwayat seri berulang.
- **Pembuat pengulangan (RRULE)**: harian/mingguan (pilih hari)/bulanan (tanggal atau akhir
  bulan)/tahunan + interval, pratinjau 5 kejadian berikutnya.
- **Pengingat push**: notifikasi lokal dengan aksi "Selesai" dan "Tunda 10 mnt"; ketuk membuka
  detail tugas. Opsi sembunyikan isi notifikasi.

### 3. Kalender — pusat aplikasi
- Grid bulan yang **menciut jadi strip minggu** saat agenda digulir; geser horizontal antar bulan.
- Tiap sel hari menampilkan **batang warna bertumpuk** per modul (bukan titik) + tint rata-rata mood.
- Chip legenda untuk menyalakan/mematikan modul.
- **Agenda hari** di bawah: gabungan tugas, jurnal, catatan bertanggal, transaksi, dan vault
  (snippet/project/versi baru), dikelompokkan per modul.
- **Tugas berulang = kejadian virtual**: garis putus-putus + ikon pengulangan ↻; ketuk untuk
  menyelesaikan kejadian itu (model bergulir).
- Tombol tambah di hari terpilih: tugas, catatan bertanggal, entri jurnal, transaksi.

### 4. Jurnal
- **Rak entri**: kartu per tanggal dengan strip warna mood; satu tanggal bisa banyak entri.
- **Editor harian layar penuh** (Markdown, toolbar format, checklist), simpan otomatis 0,7 dtk,
  indikator tersimpan.
- **Mood 1–5** sebagai penggeser 5 ikon (bukan emoji).
- Tab **Insight**: grafik mood 30/90 hari, mood per hari dalam minggu, streak,
  "hari ini di masa lalu".
- Cari teks + filter mood; templat refleksi mingguan & bulanan (dibuka dari Review).

### 5. Catatan
- **Overview** grid staggered; disematkan di atas; filter Semua / Bertanggal / Bebas.
- Catatan harian (ditempel ke tanggal, muncul di Kalender) dan catatan bebas.
- Editor Markdown yang sama dengan Jurnal, tombol "Tempel ke tanggal / Lepas", pencarian judul+isi.

### 6. Keuangan
- **Kartu saldo hero** (total semua dompet) + **karusel dompet** (multi wallet, geser antar dompet).
- Tab gulir: **Ringkasan · Transaksi · Anggaran · Berulang · Hutang · Kelola**.
  - Ringkasan: donat per kategori, batang pemasukan vs pengeluaran, kartu "Jatuh tempo".
  - Transaksi: daftar per tanggal (header lengket), filter dompet/kategori/jenis, transfer.
  - Anggaran: busur progres per kategori per bulan.
  - Berulang: seri transaksi berulang (otomatis catat / catat manual / lewati).
  - Hutang: hutang & piutang, lunasi / batal lunas, perpindahan dompet opsional.
  - Kelola: dompet (arsip), kategori **dengan ikon & warna**, ekspor CSV.
- **Input rupiah** lewat keypad sheet khusus (tombol cepat "rb", "jt", "000"), tampilan
  `Rp 1.250.000` saat diketik.

### 7. Vault Kode
- Tab **Snippet · Project · Konfigurasi**.
- Snippet: kartu dengan lencana bahasa, **salin cepat sekali ketuk**, sorotan sintaks, variabel
  templat `{{nama}}` diisi saat menyalin.
- Project: **arsip ZIP** (unggah via pemilih berkas/Share), analisis isi (file rahasia,
  node_modules, .git), deteksi duplikat SHA-256, **viewer pohon file** + pratinjau isi,
  riwayat versi & **unggah versi baru**.
- Konfigurasi: berkas `.env`/JSON berversi terenkripsi kunci brankas Sandi, nilai disamarkan,
  bandingkan per kunci antar versi/antar berkas.

### 8. Sandi — password manager terenkripsi
- Layar buka dengan **master password**; opsi **"Percayai perangkat ini 30 hari"** = DEK
  dibungkus kunci Android Keystore (non-exportable) + opsi buka dengan biometrik.
- Daftar item dengan avatar huruf; detail dengan tampilkan/salin (papan klip dibersihkan
  otomatis, ditandai sensitif); layar memakai `FLAG_SECURE`.
- **Generator** karakter (8–128) & frasa sandi + **pengukur kekuatan** (zxcvbn, kamus EN+ID).
- **TOTP**: kode + cincin hitung mundur, salin, **riwayat kode** (`pw_totp_log`).
- **Kunci pemulihan & file recovery** (simpan/impor lewat Storage Access Framework).
- **Rotasi kunci brankas** (ganti master password / pulihkan) lewat RPC atomik, dengan syarat
  online + antrean kosong.
- **Device trust**: daftar & cabut kepercayaan perangkat ini; kunci otomatis saat aplikasi di
  latar 0/1/5/15 menit.

### 9. Berkas
- Unggah file sementara ke R2 lewat Edge Function `tempfiles` (WorkManager + notifikasi progres,
  tetap jalan saat aplikasi ditutup).
- Pilih **masa kedaluwarsa** 1 jam / 1 hari / 7 hari / 30 hari / tanpa batas; bar kuota 1 GB.
- **Link publik bertoken**: salin/bagikan, jadikan privat, ganti tautan, hapus.
- **Halaman unduh publik**: App Link `…/unduh/<token>` dibuka di layar unduh dalam aplikasi
  (tanpa login), unduh via DownloadManager; penerima tanpa aplikasi tetap memakai halaman web.

### 10. Review
- Pengalih **Minggu / Bulan** + geser ke periode sebelumnya.
- Kartu statistik per modul dengan **delta vs periode lalu** (tugas selesai/terlambat, hari
  menulis & rata-rata mood, pemasukan/pengeluaran + kategori terbesar, item vault baru).
- **Heatmap 26 minggu** (Canvas, level 0–4), aturan hitung sama dengan Kalender.
- Tombol **Tulis refleksi** → langsung membuka editor Jurnal dengan templat minggu/bulan.

### 11. Pengaturan
- Tema terang/gelap/sistem · **Kunci aplikasi PIN** (+ biometrik opsional, lupa PIN = verifikasi
  password akun) · **MFA**: daftar authenticator (QR), daftar faktor, hapus faktor.
- Notifikasi pengingat (izin notifikasi & alarm tepat waktu, sembunyikan isi).
- **Status sinkronisasi & penyimpanan** (terakhir sinkron, antrean upload, ukuran DB & cache).
- **Ekspor data** JSON (SAF) · **Hapus data perangkat** · **Info akun** & nama tampilan.
- **Daftar error upload** yang ditolak server (tabel, penyebab, salin JSON, bersihkan).

### Fitur lintas modul
- **Spotlight / command palette** (geser dok ke atas, Ctrl/⌘ K): pencarian FTS5 lokal untuk
  tugas, catatan, jurnal, transaksi, snippet, project, tag + perintah + catat cepat.
- **Capture & Share Target**: intent `ACTION_SEND` (teks, URL, gambar, PDF, ZIP). Teks diparse
  otomatis → tugas / catatan / jurnal / transaksi (pratinjau jenis bisa diganti); file → lampiran,
  project/versi, snippet, struk transaksi, atau Berkas.
- **Tag**: halaman per tag lintas modul + tag picker di tugas.
- **Sampah**: soft delete & pulihkan di semua modul, purge otomatis 30 hari.
- **Musik**: Media3 ExoPlayer + MediaSession (main di latar & kontrol di notifikasi); tab
  **Cari lagu · Antrean · Lirik**; mini player di atas dok.
- **TikTok**: tempel/bagikan tautan → pratinjau video/foto karusel → unduh ke galeri.
- **Lampiran file** di tugas, jurnal, transaksi, project (presign R2 via Edge Function `files`,
  outbox WorkManager, cache lokal agar bisa dibuka offline).
- **Auth Supabase** dengan MFA wajib aal2, PIN lock lokal, ingat perangkat 30 hari.
- **Offline dulu**: semua baca/tulis ke SQLite lokal, sinkron ke Postgres saat online, multi
  perangkat (HP & laptop).

---

## 4. Arsitektur aplikasi

```
UI (Compose screens) ──► ViewModel (StateFlow) ──► Repository
                                                     │ baca/tulis LOKAL (Flow reaktif)
                                              PowerSync SQLite  ──► antrean CRUD
                                                     │
                       SupabaseConnector.uploadData (upload policy)  ──► Supabase Postgres (RLS)
                       PowerSync Service ◄── replikasi ─────────────────┘
Edge Functions: files (R2 lampiran), tempfiles (Berkas) · Worker: musik, TikTok
```

### Modul Gradle

```
app/                    Activity tunggal, NavHost, gerbang (env → auth → MFA → DB → PIN)
core/designsystem       Token "Kertas & Tinta", tema, komponen (Dok, kartu indeks, keypad, chip)
core/common             Waktu WIB, uang IDR, UUIDv7, LIMITS, util
core/domain             Model + logika murni: quick-parse, parse-capture, RRULE, recurring finance,
                        upload-policy, heatmap, review stats (diuji JVM)
core/database           Skema PowerSync (semua tabel), DAO/query, FTS5, upload_errors
core/sync               Supabase client, connector, status sinkron
core/crypto             Sandi: KDF, AES-GCM, AAD, label kunci, TOTP, generator, recovery, Keystore
core/files              Presign R2, outbox lampiran, cache, ZIP
feature/home, tasks, calendar, journal, notes, finance, vault, sandi, files, review, settings
feature/search, capture, tags, trash, music, tiktok, auth, lock
```

### Library utama
Jetpack Compose (BOM) + Material 3 + adaptive · Navigation Compose (type-safe) · Koin (DI) ·
Coroutines/Flow · kotlinx-serialization · kotlinx-datetime · PowerSync Kotlin SDK · supabase-kt ·
Ktor (OkHttp) · WorkManager · Media3 · Vico (grafik) · Landscapist · Lottie · Konfetti · Balloon ·
lib-recur (RRULE) · zxcvbn4j · multiplatform-markdown-renderer · highlights (sintaks) ·
Biometric · Glance (widget) · DataStore.

---

## 5. Tahapan pengerjaan (tiap tahap = beberapa commit, APK bisa di-build di tiap akhir tahap)

| Tahap | Isi | Hasil yang bisa dicoba |
| --- | --- | --- |
| **0. Fondasi** | Proyek Gradle multi-modul, design system "Kertas & Tinta", tema 3 mode, Dok + Ruang + Spotlight kosong, GitHub Actions build APK | APK terpasang, navigasi jalan |
| **1. Data & Auth** | supabase-kt login, MFA aal2 + ingat 30 hari, skema PowerSync lengkap, connector + upload policy + `upload_errors`, PIN lock, logout menghapus data lokal | Login & sinkron data dari webapp tampil |
| **2. Tugas + Beranda** | Quick add parser, subtask, prioritas, tag picker, filter, RRULE bergulir, alarm pengingat, undo; layar Hari Ini | Kelola tugas offline, notifikasi |
| **3. Kalender** | Query gabungan + kejadian virtual, grid bulan/strip minggu, agenda hari | Pusat aktivitas |
| **4. Jurnal + Catatan** | Editor Markdown bersama, mood & insight, cari/filter, templat | Menulis jurnal & catatan offline, muncul di Kalender |
| **5. Keuangan** | 6 tab, multi dompet, kategori ikon, grafik, input rupiah, berulang, anggaran, hutang, CSV | |
| **6. Lampiran + Vault Kode** | Presign R2, outbox, snippet, project ZIP + viewer + versi | |
| **7. Sandi + Konfigurasi** | Kripto kompatibel webapp (uji vektor), brankas, generator, TOTP + riwayat, recovery, rotasi, device trust | Brankas dari laptop terbuka di HP |
| **8. Berkas** | Tempfile upload, kedaluwarsa, link token, App Link unduh publik | |
| **9. Review** | Statistik per modul, heatmap 26 minggu, tulis refleksi | |
| **10. Lintas modul** | FTS5 Spotlight, Share Target & capture, halaman Tag, Sampah, Musik, TikTok | |
| **11. Pengaturan & rilis** | Pengaturan lengkap, ekspor, widget & shortcut, aksesibilitas (TalkBack, font besar), APK rilis bertanda tangan | APK rilis |

### Pengujian
- Uji unit JVM untuk semua logika murni di `core/domain` & `core/crypto`, termasuk **vektor uji
  lintas platform** yang dibangkitkan dari kode webapp.
- Uji query SQLite (Robolectric) untuk agregasi Kalender/Review/heatmap.
- Uji UI Compose untuk alur kritis (login → MFA → buka brankas, quick add, transaksi).

---

## 6. Konfigurasi & rahasia

Tidak ada kredensial di repo. Nilai dibaca dari `local.properties` (lokal) atau GitHub Secrets (CI)
ke `BuildConfig`:

`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `POWERSYNC_URL` (wajib) ·
`MUSIC_API_URL`, `TIKTOK_API_URL`, `PUBLIC_WEB_ORIGIN` (opsional — kosong = fiturnya disembunyikan) ·
`RELEASE_KEYSTORE_*` (tanda tangan APK rilis).

## 7. Risiko & hal yang perlu dicek

- **Worker TikTok hanya menerima Origin Taskify** → aplikasi native mungkin perlu diizinkan di
  Worker tersebut (repo terpisah).
- **Pengingat lintas perangkat**: alarm lokal hanya di HP; laptop tetap memakai Web Push dari server.
  FCM (opsional) butuh Firebase + perubahan `send-reminders` di repo webapp.
- **Pengukur kekuatan master password**: zxcvbn4j bisa berbeda tipis dari zxcvbn-ts; syarat
  ≥ 12 karakter & ≥ 50 bit tetap ditegakkan, gagal tertutup.
- **Unggahan besar (Berkas 1 GB)**: satu PUT tanpa resume (batas server); dijalankan di
  foreground worker agar tidak terputus saat aplikasi ditutup.
- **FTS5** di SQLite PowerSync: diverifikasi di tahap 10, fallback ke `LIKE`.
