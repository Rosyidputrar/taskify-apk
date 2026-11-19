package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.lyrics.LrcLibLyricsProvider
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import com.rosyidputrar.taskify.core.media.mytube.DirectStreamResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Uji live ber-gate (Tahap 9 rewrite musik): memanggil kode direct BARU
 * langsung ke InnerTube / LRCLib / SponsorBlock nyata dari IP VM.
 *
 * Meniru pola `worker/live.test.ts` di repo Worker: dilewati secara bawaan,
 * hanya jalan bila env `MUSIC_LIVE=1`. Jalankan dari repo ini:
 *
 *   MUSIC_LIVE=1 bash /tmp/run-gradle.sh :core:media:testDebugUnitTest --tests "*MyTubeLiveTest*"
 *
 * WAJIB lewat runner repo (`/tmp/run-gradle.sh`): JVM tes butuh
 * `JAVA_TOOL_OPTIONS` dari runner (proksi egress + truststore) supaya
 * OkHttp bisa keluar dari VM. Gradle polos tanpa itu akan gagal jaringan.
 *
 * Tidak ada cookie/token/visitorData di file ini: semua panggilan anonim,
 * sama seperti jalur produksi tahap 1 (PLAN G.1). Hasil per endpoint
 * dicetak ke stdout tes (masuk laporan XML) supaya bisa dikutip di
 * laporan verifikasi.
 *
 * Sifat hasil: InnerTube dari IP VM itu flaky per permintaan (`next` 403
 * pernah terjadi 17 Nov 2026, `player` kadang LOGIN_REQUIRED). Karena itu
 * tes ini membedakan kegagalan KERAS (kode kita salah / endpoint mati
 * total) dari sinyal LUNAK (upNext kosong karena `next` diblokir IP ini —
 * dicetak sebagai PERINGATAN, bukan gagal, karena `track()` punya
 * fallback `player` dan perilaku IP HP diuji terpisah di HP-TEST-CHECKLIST).
 * Pengecualian: `radio` TIDAK punya fallback `next`, jadi radio kosong
 * dari VM adalah gagal keras di sini — itu sinyal risiko F.1 yang harus
 * dianalisis sebelum merge, bukan ditutupi.
 *
 * Temuan Tahap 9 (2026-11-17): `searchCardRowsDoNotUseDurationAsArtist`
 * mendokumentasikan bug parser nyata — baris lagu kartu teratas diisi
 * artis = durasi ("3.33") — dan sengaja GAGAL sampai parser diperbaiki
 * terpisah (SearchPage.kt:138). Bukan kegagalan endpoint.
 */
class MyTubeLiveTest {

    private val live = System.getenv("MUSIC_LIVE") == "1"

    private val myTube = MyTube()
    private val resolver = DirectStreamResolver()
    private val lrcLib = LrcLibLyricsProvider()
    private val sponsorBlock = SponsorBlockRepository()

    @Before
    fun gate() {
        Assume.assumeTrue("Uji live dilewati: set MUSIC_LIVE=1 untuk menjalankannya", live)
    }

    /** Satu lagu benih dari pencarian nyata; tiap tes cari sendiri supaya independen. */
    private suspend fun seedSong(): Song {
        val results = myTube.search("tulus").getOrThrow()
        assertTrue(results.songs.isNotEmpty(), "search 'tulus' harus memberi lagu")
        val seed = results.songs.first()
        println("LIVE benih: ${seed.videoId} — ${seed.title} — ${seed.artist} (${seed.durationSeconds} dtk)")
        return seed
    }

    @Test
    fun searchTulusReturnsSongs() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val results = myTube.search("tulus").getOrThrow()
            println("LIVE search 'tulus': ${results.songs.size} lagu, cursor=${results.cursor != null}")
            results.songs.take(3).forEach { println("LIVE   lagu: ${it.videoId} — ${it.title} — ${it.artist}") }
            assertTrue(results.songs.isNotEmpty(), "search 'tulus' harus memberi lagu")
        }
    }

    /**
     * Baris lagu di kartu hasil teratas TIDAK membawa artis di subtitlenya
     * (hanya kata jenis + durasi, mis. `['Lagu', ' • ', '3.33']`), dan
     * parser mengambil `sub[1]` sebagai artis — lihat temuan Tahap 9
     * (SearchPage.kt:138): artis jadi "3.33". Tes ini mendokumentasikan
     * bug itu dan GAGAL sampai parser diperbaiki terpisah.
     */
    @Test
    fun searchCardRowsDoNotUseDurationAsArtist() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val results = myTube.search("tulus").getOrThrow()
            val bad = results.songs.filter { MusicQueue.parseDuration(it.artist) != null }
            bad.forEach { println("LIVE BUG artis=durasi: ${it.videoId} — ${it.title} — artis='${it.artist}'") }
            assertTrue(bad.isEmpty(), "artis lagu tidak boleh berupa durasi (mis. '3.33'): $bad")
        }
    }

    @Test
    fun trackGivesSongAndUpNext() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val seed = seedSong()
            val track = myTube.track(seed.videoId).getOrThrow()
            println(
                "LIVE track ${seed.videoId}: song=${track.song?.title} — ${track.song?.artist}, " +
                    "upNext=${track.upNext.size}, lyricsId=${track.lyricsId.isNotEmpty()}, " +
                    "loudnessDb=${track.loudnessDb}",
            )
            track.upNext.take(3).forEach { println("LIVE   upNext: ${it.videoId} — ${it.title}") }
            assertNotNull(track.song, "track harus memberi metadata lagu (dari next atau fallback player)")
            if (track.upNext.isEmpty()) {
                println(
                    "LIVE PERINGATAN: upNext kosong — `next` kemungkinan diblokir (403) dari IP ini " +
                        "(PLAN F.1). Bukan gagal tes: antrean serupa butuh uji dari IP HP.",
                )
            }
        }
    }

    @Test
    fun radioReturnsMix() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val seed = seedSong()
            val mix = myTube.radio(videoId = seed.videoId).getOrThrow()
            println("LIVE radio RDAMVM${seed.videoId}: ${mix.songs.size} lagu, cursor=${mix.cursor != null}")
            mix.songs.take(3).forEach { println("LIVE   radio: ${it.videoId} — ${it.title}") }
            assertTrue(mix.songs.isNotEmpty(), "radio RDAMVM harus memberi mix (next tanpa fallback)")
        }
    }

    @Test
    fun resolveStreamDirectGivesGooglevideoUrlAndRange206() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val seed = seedSong()
            val stream = resolver.resolve(seed.videoId, MusicQuality.Auto).getOrThrow()
            println(
                "LIVE resolve ${seed.videoId}: itag=${stream.itag}, mime=${stream.mimeType}, " +
                    "bitrate=${stream.bitrate}, expiresInSeconds=${stream.expiresInSeconds}, " +
                    "loudnessDb=${stream.loudnessDb}",
            )
            assertTrue(stream.url.contains("googlevideo.com"), "URL stream harus googlevideo langsung")
            assertTrue(
                stream.mimeType.contains("opus") || stream.mimeType.contains("mp4a"),
                "format Auto harus opus (251) atau mp4a (140), dapat: ${stream.mimeType}",
            )
            assertNotNull(stream.expiresInSeconds, "expiresInSeconds harus ada")
            assertTrue(stream.expiresInSeconds!! > 0, "URL harus belum kedaluwarsa")

            HttpClient(OkHttp) {
                expectSuccess = false
                // Timeout bawaan OkHttp (10 dtk) terlalu ketat untuk koneksi
                // pertama lewat proksi egress VM — curl membuktikan URL yang
                // sama dijawab 206 dalam 0,58 dtk. Timeout longgar di sini
                // hanya untuk tes live, bukan perilaku produk.
                engine {
                    config {
                        connectTimeout(30, TimeUnit.SECONDS)
                        readTimeout(60, TimeUnit.SECONDS)
                        writeTimeout(60, TimeUnit.SECONDS)
                    }
                }
            }.use { http ->
                val range = http.get(stream.url) { header(HttpHeaders.Range, "bytes=0-99") }
                println(
                    "LIVE Range bytes=0-99: ${range.status} " +
                        "Content-Range=${range.headers[HttpHeaders.ContentRange]} " +
                        "Content-Length=${range.headers[HttpHeaders.ContentLength]}",
                )
                assertEquals(HttpStatusCode.PartialContent, range.status, "googlevideo harus jawab 206 untuk Range")
            }
        }
    }

    @Test
    fun lrcLibGivesSyncedLyrics() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            // Artis diambil dari `track()` (parser `next`), BUKAN dari benih
            // pencarian: baris kartu teratas salah mengisi artis dengan
            // durasi (temuan Tahap 9, SearchPage.kt:138) dan LRCLib akan
            // gagal dengan artis yang salah — itu bug parser, bukan LRCLib.
            val seed = seedSong()
            val song = myTube.track(seed.videoId).getOrThrow().song
            assertNotNull(song, "track harus memberi metadata lagu")
            println("LIVE LRCLib memakai: '${song.title}' — ${song.artist} (${song.durationSeconds} dtk)")
            val lyrics = lrcLib.fetchLyrics(song.title, song.artist, duration = song.durationSeconds ?: -1)
            assertNotNull(lyrics, "LRCLib harus punya lirik untuk '${song.title}' — ${song.artist}")
            println(
                "LIVE LRCLib '${song.title}': synced=${lyrics.synced?.size} baris, " +
                    "plain=${lyrics.plain.length} karakter, instrumental=${lyrics.instrumental}",
            )
            lyrics.synced?.firstOrNull()?.let { println("LIVE   baris pertama: [${it.t}] ${it.text}") }
            assertTrue(
                lyrics.synced?.isNotEmpty() == true || lyrics.plain.isNotBlank(),
                "LRCLib harus memberi lirik sinkron atau teks",
            )
        }
    }

    @Test
    fun sponsorBlockResponds() = runBlocking {
        withTimeout(LIVE_TIMEOUT_MS) {
            val seed = seedSong()
            // Tidak ada assert jumlah: video musik umumnya 0 segmen (404 -> list kosong).
            // Yang diuji: API sponsor.ajay.app menjawab tanpa galat dari IP VM.
            val segments = sponsorBlock.segments(seed.videoId)
            println("LIVE SponsorBlock ${seed.videoId}: ${segments.size} segmen")
            segments.forEach { println("LIVE   segmen: $it") }
        }
    }

    private companion object {
        const val LIVE_TIMEOUT_MS = 90_000L
    }
}
