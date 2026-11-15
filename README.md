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
app/                 Shell: navigasi Dok/Rail, Ruang, Spotlight, Tangkap, Pengaturan
core/common          Logika murni: rupiah, UUIDv7, tanggal WIB
core/designsystem    "Kertas & Tinta": palet, tipografi, kartu indeks, Dok
build-logic/         Convention plugin Gradle
```

Font: Fraunces, Instrument Sans, JetBrains Mono (SIL Open Font License, lihat `licenses/`).
