# Catatan lisensi: kode yang diadaptasi dari ArchiveTune

Rewrite backend musik Taskify (direct-to-YouTube) memakai ArchiveTune
(v15.1.0, komit `c7f0e24`, https://github.com/rukamori/ArchiveTune)
sebagai salah satu referensi. ArchiveTune berlisensi **GPL-3.0**; teks
lengkap lisensinya ada di `licenses/GPL-3.0.txt`.

## File yang diadaptasi dari ArchiveTune (GPL-3.0)

File-file ini mempertahankan header lisensi asli ArchiveTune di baris
pertama, dengan catatan adaptasi tepat di bawahnya. Paket diubah dari
`moe.rukamori.archivetune.*` ke `com.rosyidputrar.taskify.core.innertube.*`.

| File di repo ini | Sumber ArchiveTune |
|---|---|
| `core/innertube/src/main/kotlin/.../innertube/InnerTube.kt` | `core/.../innertube/InnerTube.kt` (dipangkas: tanpa NetworkGatekeeper, tanpa endpoint akun; metode mengembalikan JSON mentah; token lanjutan di badan; locale bawaan id/ID; header ditambah Accept-Language + X-Goog-AuthUser dari Worker) |
| `core/innertube/src/main/kotlin/.../innertube/models/YouTubeClient.kt` | `core/.../innertube/models/YouTubeClient.kt` (+ aksesori `ANDROID`) |
| `core/innertube/src/main/kotlin/.../innertube/models/Context.kt` | `core/.../innertube/models/Context.kt` |
| `core/innertube/src/main/kotlin/.../innertube/models/YouTubeLocale.kt` | `core/.../innertube/models/YouTubeLocale.kt` |
| `core/innertube/src/main/kotlin/.../innertube/PlaybackAuthState.kt` | `core/.../innertube/PlaybackAuthState.kt` |
| `core/innertube/src/main/kotlin/.../innertube/utils/AuthUtils.kt` | `core/.../innertube/utils/Utils.kt` (hanya sha1 + cookie login; helper playlist/library tidak dibawa) |
| `core/innertube/src/main/resources/innertube/youtube_clients.json` | `core/src/main/resources/innertube/youtube_clients.json` (+ profil `ANDROID` 20.10.4) |
| `core/innertube/.../models/body/Bodies.kt` (`GetTranscriptBody` saja) | `core/.../innertube/models/body/GetTranscriptBody.kt` (sisa file ditulis baru, lihat bawah) |
| `core/innertube/.../InnerTube.kt` (`getTranscript` saja) | `core/.../innertube/InnerTube.kt#getTranscript` (tanpa param `key` dan tanpa PoToken di tahap 1) |
| `core/innertube/.../pages/TranscriptPage.kt` | `core/.../innertube/YouTube.kt#transcript` (logika cue group -> LRC; ditulis ulang di atas `JsonElement`) |
| `core/media/.../lyrics/LyricsProvider.kt` | `app/.../lyrics/LyricsProvider.kt` (kontrak sama; `isEnabled` tanpa `Context`) |
| `core/media/.../lyrics/YouTubeLyricsProvider.kt` | `app/.../lyrics/YouTubeLyricsProvider.kt` (alur `next` -> lyricsEndpoint -> `browse`; lewat fasad `:core:innertube`) |
| `core/media/.../lyrics/YouTubeTranscriptProvider.kt` | `app/.../lyrics/YouTubeSubtitleLyricsProvider.kt` (tanpa PoToken di tahap 1) |
| `core/media/.../sponsorblock/SponsorBlockModels.kt` | `app/.../sponsorblock/SponsorBlockModels.kt` (kategori memakai 12 slug terbukti Worker; segmen dipetakan ke model domain Taskify) |
| `core/media/.../sponsorblock/SponsorBlockRepository.kt` | `app/.../sponsorblock/SponsorBlockRepository.kt` (tanpa DataStore/Hilt; validasi kategori ala Worker; `actionTypes` + `"mute"` supaya `SponsorBlock.muted()` tetap bekerja) |

Yang secara sadar TIDAK disalin: `NetworkGatekeeper.kt` (kill-switch
verifikasi build — dilarang PLAN A.9), mesin PoToken/BotGuard, resolver
QuickJS/`youtubei.js`, dan seluruh parser `pages/*` ArchiveTune.

## File yang ditulis baru (bukan turunan ArchiveTune)

Ditulis dari perilaku Worker music-taskify (`worker/innertube.ts`,
`worker/direct.ts`, `src/ytmusic/parse.ts`) atau dari spesifikasi di
`~/workspace/music-direct-rewrite/PLAN.md`:

- `core/innertube/.../SearchFilter.kt` (nilai `params` Worker),
- `core/innertube/.../models/body/Bodies.kt` (bentuk badan InnerTube),
- `core/innertube/.../MusicBackend.kt` (seam auth/locale minimal),
- seluruh parser `core/innertube/.../pages/*` dan fasad `YouTube.kt`
  (Tahap 2; port perilaku `parse.ts`, keluarannya model Taskify),
- `core/media/.../youtube/DirectStreamResolver.kt` dan `StreamFailure.kt`
  (Tahap 3; aturan `pickAudioFormat` Worker).
- `core/media/.../lyrics/LrcLibLyricsProvider.kt` dan `LrcParser.kt`
  (Tahap 4; port `worker/synced-lyrics.ts` + `worker/lrc.ts` — aturan
  `/api/get` lalu `/api/search`, toleransi 10/15 dtk, parser LRC).
- `core/media/.../lyrics/LyricsRepository.kt` (Tahap 4; port
  `resolveSynced` di `worker/index.ts` — urutan LRCLib -> teks YouTube
  -> transkrip, `synced: []` = instrumental).

## Keputusan akhir lisensi

Ada di tangan sir. Selama file adaptasi GPL-3.0 di atas tetap berada di
repo, gabungan ini tunduk pada ketentuan GPL-3.0 (bagian F.7 PLAN).
