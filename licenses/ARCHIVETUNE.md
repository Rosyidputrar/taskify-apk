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
| `core/innertube/.../pages/AccountMenuParser.kt` | `core/.../innertube/YouTube.kt:2499-2554` (`accountInfo`/`accountDataSyncId`) + `models/response/AccountMenuResponse.kt` (ditulis ulang di atas `JsonElement`; hanya `dataSyncId` akun terpilih, tanpa `AccountChannel`) |
| `feature/media/.../MusicLoginScreen.kt` | `app/.../ui/screens/LoginScreen.kt` (WebView login + `YouTubeLoginWebViewClient` + `LOGIN_CONTEXT_SCRIPT` verbatim + `mergeYouTubeCookies`; tanpa Hilt/NavController/Toast; pembersih sesi ditulis di file yang sama; state Loading/Error sebagai composable statis) |
| `feature/media/.../MusicLoginViewModel.kt` | `app/.../viewmodels/LoginViewModel.kt` (state Loading/Success/Error + guard cookie ganda; tanpa Hilt, tanpa `UpdateYouTubeLoginContextUseCase`; pesan error = `MusicLoginError.message` bahasa Indonesia) |
| `feature/media/.../MusicLoginWebUtils.kt` | `app/.../ui/screens/LoginScreen.kt` (helper URL/cookie `YouTubeLoginWebViewClient` + `mergeYouTubeCookies`, ditulis ulang JVM-murni dengan `java.net.URI`) |

| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiResolver.kt` | `morideobfuscator/.../youtubei/YoutubeiResolver.kt` (verbatim kecuali nama paket + nama thread) |
| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiQuickJsWorker.kt` | `morideobfuscator/.../youtubei/YoutubeiQuickJsWorker.kt` (verbatim kecuali nama paket + nama thread + nama berkas debug `taskify-*.js`) |
| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiHttpClient.kt` | `morideobfuscator/.../youtubei/YoutubeiHttpClient.kt` (verbatim kecuali nama paket) |
| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiDiskCache.kt` | `morideobfuscator/.../youtubei/YoutubeiDiskCache.kt` (verbatim kecuali nama paket) |
| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiModels.kt` | `morideobfuscator/.../youtubei/YoutubeiModels.kt` (verbatim kecuali nama paket) |
| `core/youtubei/src/main/kotlin/.../youtubei/YoutubeiRequestAuthentication.kt` | `morideobfuscator/.../youtubei/YoutubeiRequestAuthentication.kt` (verbatim kecuali nama paket) |
| `core/youtubei/src/main/assets/youtubei/youtubei.bundle.js` + `runtime-polyfills.js` | `morideobfuscator/src/main/assets/youtubei/*` (verbatim, md5 `c2342b8a...` / `46d1ba45...`; rebuild dari sumber terbukti setara fungsional, lihat `core/youtubei/README.md`) |
| `core/youtubei/javascript/` (sumber bundle: `package.json`, `src/`, `scripts/`) | `morideobfuscator/javascript/` (verbatim, untuk rebuild bundle) |
| `core/media/.../youtube/YoutubeiStreamResolver.kt` | fasad baru di atas `YoutubeiResolver` (pemetaan ke `DirectStream`/`StreamFailure` + fallback ke jalur direct; ditulis baru) |
| `core/media/.../youtube/potoken/BotGuardTokenGenerator.kt` | `app/.../utils/potoken/BotGuardTokenGenerator.kt` (verbatim kecuali nama paket + Timber → Log + ArrayMap → LinkedHashMap + `Response.body` nullable OkHttp 4.12) |
| `core/media/.../youtube/potoken/ChallengeParser.kt` | `app/.../utils/potoken/ChallengeParser.kt` (verbatim kecuali nama paket) |
| `core/media/.../youtube/potoken/PoTokenResult.kt` | `app/.../utils/potoken/PoTokenResult.kt` (verbatim kecuali nama paket) |
| `core/media/.../youtube/potoken/PoTokenException.kt` | `app/.../utils/potoken/PoTokenException.kt` (verbatim kecuali nama paket) |
| `app/src/main/assets/po_token.html` | `app/src/main/assets/po_token.html` (verbatim, md5 `3a773db9...`) |
| `core/media/.../youtube/PoTokenEnsurer.kt` | fasad baru (padanan `YTPlayerUtils.ensureYoutubeiPoTokensForPlayback` + pre-warm; ditulis baru, best-effort) |
| `core/media/.../youtube/FailedStreamClients.kt` | `app/.../utils/YTPlayerUtils.kt` (`markStreamClientFailed` / `isStreamClientTemporarilyBlocked` / `buildFailedClientKey` + konstanta; `YouTube.currentPlaybackAuthState()` diganti parameter fingerprint eksplisit) |
| `core/media/.../youtube/PlaybackStreamRecoveryTracker.kt` | `app/.../playback/PlaybackStreamRecoveryTracker.kt` (verbatim kecuali paket + metode tambahan `reset()` — percobaan putar baru mendapat jatah segar, lihat KDoc) |
| `core/media/.../MusicPlayer.kt` (pemulihan galat Tahap 7) | `app/.../playback/MusicService.kt` (`handlePlaybackError` / `retryPlaybackAfterStreamFailure` / `capturePlaybackRecoverySnapshot` / `isTransientNetworkFailure` / `isRetryableRemoteParserFailure` / `isBotDetectionError`) + `YTPlayerUtils.recoverFromBadStreamPlayerResponse` (diadaptasi ke state machine `MusicQueue`; tanpa auto-resume — URL baru di-stage, posisi dipakai saat putar berikut) |
| `core/innertube/.../InnerTube.kt` (`registerPlayback` + konstanta telemetri; Tahap 9) | `core/.../innertube/InnerTube.kt:409-431` (`registerPlayback` + `ytPlaybackTrackingClient` + `PLAYBACK_TELEMETRY_*`; `ytClient` dipakai ulang dengan override Accept-Language/Cache-Control) |
| `core/innertube/.../YouTube.kt` (`registerPlayback` + `CPN_ALPHABET`; Tahap 9) | `core/.../innertube/YouTube.kt:2198-2225` (generasi `cpn` 16 karakter; tanpa pencatatan riwayat lokal) |
| `core/media/.../MusicPlayer.kt` (telemetri putar Tahap 9) | `app/.../playback/MusicService.kt:6879,6938-7010` (`registerRemotePlaybackHistory` + cache URL tracking per lagu; diadaptasi: dipicu saat lagu mulai berbunyi, gagal = URL dibuang tanpa refresh metadata) |

Yang secara sadar TIDAK disalin: `NetworkGatekeeper.kt` (kill-switch
verifikasi build — dilarang PLAN A.9) dan seluruh parser `pages/*`
ArchiveTune.

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
