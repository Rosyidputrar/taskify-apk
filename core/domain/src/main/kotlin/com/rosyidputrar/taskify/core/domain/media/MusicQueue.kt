package com.rosyidputrar.taskify.core.domain.media

import kotlin.random.Random

/**
 * Pemutar musik — port `queue-core.ts`, `queue-settle.ts`, `search-core.ts`, `music-view.ts`
 * webapp. Musik tidak menyentuh database: semua keadaan hidup di memori selama sesi.
 */
data class Thumb(val url: String, val width: Int, val height: Int)

/**
 * Satu lagu/episode/stasiun. [durationSeconds] = hasil urai server (`null` = tidak diketahui,
 * BUKAN 0). [live] = siaran radio langsung: [url] adalah URL stream yang diputar apa adanya,
 * tanpa konversi, lirik, antrean lanjutan, maupun seek.
 */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnails: List<Thumb>,
    val url: String = "",
    val durationSeconds: Int? = null,
    val live: Boolean = false,
) {
    /** Durasi dalam detik: angka server lebih dulu, lalu teks (untuk data lama/tes). */
    val seconds: Int? get() = durationSeconds ?: MusicQueue.parseDuration(duration)
    /** Gambar terkecil (daftar) & terbesar (sampul). */
    val thumb: String get() = thumbnails.firstOrNull()?.url.orEmpty()
    val cover: String get() = thumbnails.filter { it.url.isNotEmpty() }.maxByOrNull { it.width }?.url.orEmpty()
}

data class Track(
    val videoId: String,
    val lyricsId: String,
    val upNext: List<Song>,
    val durationSeconds: Int? = null,
    val loudnessDb: Double? = null,
    /** Lirik inline (`includeLyrics`); `null` = tidak diminta / tidak ada. */
    val lyrics: Lyrics? = null,
    /** `true` bila lirik diminta dan server sudah mencarinya (ada maupun tidak). */
    val lyricsChecked: Boolean = false,
    /** Lagu yang diminta itu sendiri (judul/artis/gambar) — untuk memutar dari link atau saran. */
    val song: Song? = null,
)

data class TrackDetails(
    val lyricsId: String,
    val upNext: List<Song>,
    val loudnessDb: Double? = null,
    val lyrics: Lyrics? = null,
    val lyricsChecked: Boolean = false,
)

/** Asal audio hasil resolve direct: hanya `direct` (URL `*.googlevideo.com`, mendukung `Range`) atau `live` (siaran radio, URL apa adanya). */
data class StreamSource(
    val source: String,
    val mimeType: String?,
    val itag: Int?,
    /** Waktu (epoch ms) URL direct kedaluwarsa (`expiresInSeconds`); `null` = tidak berbatas. */
    val expiresAt: Long? = null,
    val loudnessDb: Double? = null,
    /**
     * Kekerasan perseptual format ini (Tahap 8, C2-26): dipakai normalisasi
     * SEBELUM `loudnessDb` (ArchiveTune `normalizationLoudnessDb`).
     */
    val perceptualLoudnessDb: Double? = null,
    /**
     * Nama klien InnerTube yang menghasilkan URL ini (`ANDROID`/`WEB_REMIX`/`MWEB`);
     * `null` = tidak diketahui (mis. siaran langsung atau stream lama). Dipakai
     * diagnostik 403: tanpa ini tidak bisa dibedakan URL ANDROID dari WEB_REMIX.
     */
    val resolvedClient: String? = null,
    /** Sisa umur URL dalam detik dari `streamingData.expiresInSeconds`; `null` = hulu tidak memberinya. */
    val expiresInSeconds: Int? = null,
    /** Panjang isi audio dalam byte (`contentLength` format / `clen` URL); `null` = tidak diketahui. Dipakai mengisi `length` DataSpec supaya request media pertama membawa `Range`. */
    val contentLength: Long? = null,
    /** Waktu (epoch ms) URL ini di-resolve; `null` = tidak dicatat. Dipakai menghitung jeda resolve→putar. */
    val resolvedAtMs: Long? = null,
) {
    /** Jalur direct yang memilih opus (itag 251) — perlu dilacak, bisa gagal di perangkat tertentu. */
    val isOpus: Boolean get() = mimeType?.contains("opus", ignoreCase = true) == true
    /** Hanya direct yang mendukung `Range`; siaran langsung TIDAK bisa di-seek. */
    val seekable: Boolean get() = source == SOURCE_DIRECT

    companion object {
        const val SOURCE_DIRECT = "direct"
        const val SOURCE_LIVE = "live"
    }
}

/** Antrean yang berasal dari mix radio: lanjutannya diambil lewat cursor radio (49 lagu sekaligus). */
data class RadioSource(val playlistId: String, val cursor: String?, val pending: Boolean = false)

enum class RepeatMode(val label: String) { Off("Ulang: mati"), All("Ulang semua"), One("Ulang satu lagu") }

enum class YtStatus { Idle, Preparing, Playing, Paused, Error }

data class YtState(
    val queue: List<Song> = emptyList(),
    val order: List<Int> = emptyList(),
    val position: Int = 0,
    val status: YtStatus = YtStatus.Idle,
    val percent: Int = 0,
    val label: String = "",
    val error: String = "",
    /**
     * Detail mentah error pemutar terakhir (dari `PlaybackException`,
     * disusun [com.rosyidputrar.taskify.core.media.PlaybackException.toRawDetail]):
     * sudah dibersihkan dari nilai sensitif, tampil di layar musik di bawah
     * pesan ramah. `null` = tidak ada detail (atau error bukan dari pemutar).
     */
    val errorDetail: String? = null,
    val urls: Map<String, String> = emptyMap(),
    val prefetching: List<String> = emptyList(),
    val repeat: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val details: Map<String, TrackDetails> = emptyMap(),
    val detailsPending: List<String> = emptyList(),
    /** Asal audio per lagu, dari respons init/poll (item 10-klien). */
    val streams: Map<String, StreamSource> = emptyMap(),
    val continued: List<String> = emptyList(),
    val autoContinue: Boolean = true,
    val awaitingMore: Boolean = false,
    val radio: RadioSource? = null,
    /**
     * Lagu yang antreannya diisi lagu serupa begitu keterangannya datang (upNext dari
     * now-playing/track) — dipakai saat memutar satu lagu dari hasil cari/Jelajah.
     */
    val similarSeed: String? = null,
) {
    val current: Song? get() = order.getOrNull(position)?.let { queue.getOrNull(it) }
    val currentUrl: String? get() = current?.let { urls[it.videoId] }
    val lyricsId: String get() = current?.let { details[it.videoId]?.lyricsId }.orEmpty()
    /** Lagu sesuai urutan putar (memperhitungkan acak). */
    val ordered: List<Song> get() = order.mapNotNull { queue.getOrNull(it) }
    val currentStream: StreamSource? get() = current?.let { streams[it.videoId] }
    /** Boleh di-seek? Hanya bila asal audio lagu ini sudah diketahui mendukung `Range`. */
    val seekable: Boolean get() = current?.live != true && currentStream?.seekable == true
}

sealed interface YtEvent {
    data class SetQueue(val songs: List<Song>, val startAt: Int = 0, val rng: Random = Random.Default, val radio: RadioSource? = null, val similar: Boolean = false) : YtEvent
    /** Antrean tersimpan dipulihkan (setelah proses mati) — dalam keadaan jeda, URL diminta ulang saat diputar. */
    data class Restore(val snapshot: QueueSnapshot) : YtEvent
    /** Pemutaran gagal: langsung jadi galat (tidak ada lagi jalur konverter). */
    data class PlaybackFailed(val videoId: String, val message: String, val rawDetail: String? = null) : YtEvent
    /** Lupakan URL yang kedaluwarsa (direct ±6 jam) supaya diminta ulang. */
    data class Expire(val now: Long) : YtEvent
    data object RadioStart : YtEvent
    data class RadioPage(val songs: List<Song>, val cursor: String?, val rng: Random = Random.Default) : YtEvent
    data object RadioFailed : YtEvent
    data object Preparing : YtEvent
    data class Progress(val percent: Int, val label: String) : YtEvent
    data class Ready(val videoId: String, val url: String) : YtEvent
    /** Asal audio diketahui (init langsung / poll konverter selesai). */
    data class StreamInfo(val videoId: String, val info: StreamSource) : YtEvent
    data object Playing : YtEvent
    data object Paused : YtEvent
    data object Ended : YtEvent
    data object Next : YtEvent
    data object Prev : YtEvent
    data class Jump(val position: Int) : YtEvent
    /** Galat resolve / komposisi: `skippable = false` = galat sesi login — antrean TIDAK boleh auto-skip (Agen B); pengguna harus masuk lagi, bukan melewati lagu. */
    data class Error(val message: String, val rawDetail: String? = null, val skippable: Boolean = true) : YtEvent
    data class PrefetchStart(val videoId: String) : YtEvent
    data class PrefetchDone(val videoId: String, val url: String) : YtEvent
    data class PrefetchFailed(val videoId: String) : YtEvent
    /** Prefetch dibatalkan (lagu berubah) — bukan gagal, hanya tidak lagi ditunggu. */
    data class PrefetchCancelled(val videoId: String) : YtEvent
    data object ToggleRepeat : YtEvent
    data class ToggleShuffle(val rng: Random = Random.Default) : YtEvent
    data class DetailsStart(val videoId: String) : YtEvent
    data class DetailsDone(val videoId: String, val details: TrackDetails) : YtEvent
    data class DetailsFailed(val videoId: String) : YtEvent
    /** Pengambilan keterangan batal (lagu berganti) — boleh diambil lagi nanti. */
    data class DetailsCancelled(val videoId: String) : YtEvent
    data class ContinueQueue(val from: String, val songs: List<Song>, val rng: Random = Random.Default) : YtEvent
    data object ToggleAutoContinue : YtEvent
    data object Clear : YtEvent
}

object MusicQueue {
    fun nextPosition(s: YtState, manual: Boolean = false): Int? {
        if (s.order.isEmpty()) return null
        if (s.repeat == RepeatMode.One && !manual) return s.position
        val c = s.position + 1
        if (c < s.order.size) return c
        return if (s.repeat == RepeatMode.All) 0 else null
    }

    fun prevPosition(s: YtState): Int? {
        if (s.order.isEmpty()) return null
        if (s.position > 0) return s.position - 1
        return if (s.repeat == RepeatMode.All) s.order.size - 1 else null
    }

    private fun nextIgnoringRepeatOne(s: YtState): Int? {
        val c = s.position + 1
        if (c < s.order.size) return c
        return if (s.repeat == RepeatMode.All) 0 else null
    }

    /** Lagu berikut yang perlu disiapkan lebih dulu (supaya pergantian lagu tidak menunggu resolve). */
    fun prefetchTarget(s: YtState): String? {
        if (s.status != YtStatus.Playing && s.status != YtStatus.Paused) return null
        val pos = if (s.repeat == RepeatMode.One) null else nextIgnoringRepeatOne(s)
        val song = pos?.let { s.order.getOrNull(it) }?.let { s.queue.getOrNull(it) } ?: return null
        if (song.live) return null
        if (s.urls.containsKey(song.videoId) || song.videoId in s.prefetching) return null
        return song.videoId
    }

    /** Lagu yang keterangannya (lirik + antrean lanjutan) perlu diambil. */
    fun detailsTarget(s: YtState): String? {
        if (s.status != YtStatus.Playing && s.status != YtStatus.Paused) return null
        val song = s.current ?: return null
        if (song.live) return null
        if (s.details.containsKey(song.videoId) || song.videoId in s.detailsPending) return null
        return song.videoId
    }

    /** Cursor radio yang perlu diambil: lagu terakhir sedang diputar & antrean berasal dari radio. */
    fun radioTarget(s: YtState): String? {
        val r = s.radio ?: return null
        if (!s.autoContinue || s.repeat != RepeatMode.Off || r.pending) return null
        val cursor = r.cursor ?: return null
        if (s.order.isEmpty() || s.position < s.order.size - 2) return null
        if (s.status != YtStatus.Playing && s.status != YtStatus.Paused && !s.awaitingMore) return null
        return cursor
    }

    /** Antrean lanjutan dari hulu bila lagu terakhir sedang diputar. */
    fun continuationSource(s: YtState): Pair<String, List<Song>>? {
        if (!s.autoContinue || s.repeat != RepeatMode.Off) return null
        if (s.radio?.cursor != null) return null // radio punya lanjutan sendiri
        if (s.order.isEmpty() || s.position != s.order.size - 1) return null
        val song = s.current ?: return null
        if (song.videoId in s.continued) return null
        val detail = s.details[song.videoId] ?: return null
        if (detail.upNext.isEmpty()) return null
        val known = s.queue.map { it.videoId }.toSet()
        val songs = detail.upNext.filter { it.videoId.isNotEmpty() && it.videoId !in known }
        return if (songs.isEmpty()) null else song.videoId to songs
    }

    /** Fisher–Yates; [keepFirst] tetap di depan (lagu yang sedang diputar). */
    fun shuffleOrder(indices: List<Int>, keepFirst: Int?, rng: Random): List<Int> {
        val rest = indices.filter { it != keepFirst }.toMutableList()
        for (i in rest.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = rest[i]; rest[i] = rest[j]; rest[j] = t
        }
        return if (keepFirst == null) rest else listOf(keepFirst) + rest
    }

    private fun sequential(n: Int) = List(n) { it }

    private fun dedupe(songs: List<Song>): List<Song> {
        val seen = HashSet<String>()
        return songs.filter { it.videoId.isNotEmpty() && seen.add(it.videoId) }
    }

    fun reduce(state: YtState, event: YtEvent): YtState = when (event) {
        is YtEvent.SetQueue -> {
            val songs = dedupe(event.songs)
            if (songs.isEmpty()) YtState(urls = state.urls, details = state.details, streams = state.streams)
            else {
                val startAt = event.startAt.coerceIn(0, songs.size - 1)
                val order = if (state.shuffle) shuffleOrder(sequential(songs.size), startAt, event.rng) else sequential(songs.size)
                val live = songs.filter { it.live && it.url.isNotEmpty() }
                state.copy(queue = songs, order = order, position = if (state.shuffle) 0 else startAt, status = YtStatus.Preparing,
                    percent = 0, label = "", error = "", errorDetail = null, prefetching = emptyList(), continued = emptyList(), awaitingMore = false,
                    radio = event.radio,
                    similarSeed = if (event.similar) songs[startAt].videoId.takeIf { !songs[startAt].live } else null,
                    urls = state.urls + live.associate { it.videoId to it.url },
                    streams = state.streams + live.associate { it.videoId to StreamSource(StreamSource.SOURCE_LIVE, null, null) })
            }
        }
        is YtEvent.Restore -> {
            val snap = event.snapshot
            val songs = dedupe(snap.queue)
            if (songs.isEmpty()) state
            else {
                val order = snap.order.takeIf { o -> o.size == songs.size && o.sorted() == sequential(songs.size) } ?: sequential(songs.size)
                val live = songs.filter { it.live && it.url.isNotEmpty() }
                state.copy(queue = songs, order = order, position = snap.position.coerceIn(0, order.size - 1), status = YtStatus.Paused,
                    repeat = snap.repeat, shuffle = snap.shuffle, autoContinue = snap.autoContinue, radio = snap.radio?.copy(pending = false),
                    urls = state.urls + live.associate { it.videoId to it.url },
                    streams = state.streams + live.associate { it.videoId to StreamSource(StreamSource.SOURCE_LIVE, null, null) })
            }
        }
        is YtEvent.PlaybackFailed -> {
            // Langsung galat: tidak ada lagi fallback konverter (rewrite direct).
            // Siaran langsung menyimpan URL-nya (bisa dicoba lagi apa adanya);
            // lagu biasa membuang URL/stream yang gagal supaya diminta ulang.
            if (state.current?.videoId != event.videoId) {
                state.copy(urls = state.urls - event.videoId, streams = state.streams - event.videoId)
            } else if (state.current?.live == true) {
                state.copy(status = YtStatus.Error, error = event.message, errorDetail = event.rawDetail, percent = 0, label = "")
            } else {
                state.copy(urls = state.urls - event.videoId, streams = state.streams - event.videoId,
                    status = YtStatus.Error, error = event.message, errorDetail = event.rawDetail, percent = 0, label = "")
            }
        }
        is YtEvent.Expire -> {
            val expired = state.streams.filter { (_, v) -> v.expiresAt != null && v.expiresAt <= event.now + EXPIRY_MARGIN_MS }.keys
            if (expired.isEmpty()) state else state.copy(urls = state.urls - expired, streams = state.streams - expired)
        }
        YtEvent.RadioStart -> state.radio?.let { state.copy(radio = it.copy(pending = true)) } ?: state
        YtEvent.RadioFailed -> state.radio?.let { state.copy(radio = it.copy(pending = false, cursor = null), awaitingMore = false) } ?: state
        is YtEvent.RadioPage -> {
            val radio = state.radio?.copy(cursor = event.cursor, pending = false)
            val next = reduce(state.copy(radio = radio), YtEvent.ContinueQueue("radio", event.songs, event.rng))
            // Halaman radio kosong bukan galat; bila tak ada lagu baru & tak ada cursor, berhenti menunggu.
            if (next.awaitingMore && event.cursor == null) next.copy(awaitingMore = false) else next
        }
        YtEvent.Preparing -> state.copy(status = YtStatus.Preparing, percent = 0, label = "", error = "", errorDetail = null)
        is YtEvent.Progress -> if (state.status != YtStatus.Preparing) state else state.copy(percent = event.percent, label = event.label)
        is YtEvent.Ready -> state.copy(urls = state.urls + (event.videoId to event.url), percent = 100, label = "")
        is YtEvent.StreamInfo -> state.copy(streams = state.streams + (event.videoId to event.info))
        YtEvent.Playing -> state.copy(status = YtStatus.Playing, error = "", errorDetail = null, percent = 100, label = "", awaitingMore = false)
        YtEvent.Paused -> if (state.status == YtStatus.Playing) state.copy(status = YtStatus.Paused) else state
        YtEvent.Ended -> {
            val pos = nextPosition(state)
            if (pos == null) state.copy(status = YtStatus.Paused, awaitingMore = state.autoContinue && state.repeat == RepeatMode.Off && state.current?.live != true)
            else {
                val same = pos == state.position
                state.copy(position = pos, status = if (same && state.currentUrl != null) YtStatus.Playing else YtStatus.Preparing, percent = 0, label = "")
            }
        }
        YtEvent.Next, YtEvent.Prev -> {
            val pos = if (event == YtEvent.Next) nextPosition(state, manual = true) else prevPosition(state)
            if (pos == null) state else state.copy(position = pos, status = YtStatus.Preparing, percent = 0, label = "", error = "", errorDetail = null, awaitingMore = false)
        }
        is YtEvent.Jump -> if (event.position !in state.order.indices) state
            else state.copy(position = event.position, status = YtStatus.Preparing, percent = 0, label = "", error = "", errorDetail = null)
        // Galat baru (bukan dari pemutar) mengganti detail lama: detail basi
        // tidak boleh tampil di bawah pesan error yang berbeda. Error resolve
        // membawa rawDetail-nya sendiri (jenis StreamFailure + status HTTP);
        // tanpa rawDetail, detail lama dibersihkan.
        is YtEvent.Error -> state.copy(status = YtStatus.Error, error = event.message, errorDetail = event.rawDetail, percent = 0, label = "")
        is YtEvent.PrefetchStart -> if (event.videoId in state.prefetching) state else state.copy(prefetching = state.prefetching + event.videoId)
        is YtEvent.PrefetchDone -> state.copy(urls = state.urls + (event.videoId to event.url), prefetching = state.prefetching - event.videoId)
        is YtEvent.PrefetchFailed -> state.copy(prefetching = state.prefetching - event.videoId)
        is YtEvent.PrefetchCancelled -> state.copy(prefetching = state.prefetching - event.videoId)
        is YtEvent.DetailsStart -> if (event.videoId in state.detailsPending) state else state.copy(detailsPending = state.detailsPending + event.videoId)
        is YtEvent.DetailsDone -> {
            val s = state.copy(details = state.details + (event.videoId to event.details), detailsPending = state.detailsPending - event.videoId)
            // Antrean "lagu serupa": upNext lagu benih langsung disambung (tidak menunggu lagu terakhir).
            if (event.videoId == state.similarSeed) reduce(s, YtEvent.ContinueQueue(event.videoId, event.details.upNext)).copy(similarSeed = null) else s
        }
        is YtEvent.DetailsFailed -> state.copy(
            similarSeed = state.similarSeed.takeIf { it != event.videoId },
            details = state.details + (event.videoId to TrackDetails("", emptyList())),
            detailsPending = state.detailsPending - event.videoId, awaitingMore = false,
        )
        is YtEvent.DetailsCancelled -> state.copy(detailsPending = state.detailsPending - event.videoId)
        is YtEvent.ContinueQueue -> {
            val known = state.queue.map { it.videoId }.toSet()
            val fresh = event.songs.filter { it.videoId.isNotEmpty() && it.videoId !in known }
            val continued = if (event.from in state.continued) state.continued else state.continued + event.from
            if (fresh.isEmpty()) state.copy(continued = continued)
            else {
                val queue = state.queue + fresh
                val added = fresh.indices.map { state.queue.size + it }
                val order = state.order + if (state.shuffle) shuffleOrder(added, null, event.rng) else added
                if (state.awaitingMore) state.copy(queue = queue, order = order, continued = continued, position = state.position + 1,
                    status = YtStatus.Preparing, percent = 0, label = "", awaitingMore = false)
                else state.copy(queue = queue, order = order, continued = continued)
            }
        }
        YtEvent.ToggleAutoContinue -> state.copy(autoContinue = !state.autoContinue)
        YtEvent.ToggleRepeat -> state.copy(repeat = when (state.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off })
        is YtEvent.ToggleShuffle -> {
            val shuffle = !state.shuffle
            val currentIdx = state.order.getOrNull(state.position)
            when {
                state.order.isEmpty() -> state.copy(shuffle = shuffle)
                shuffle -> state.copy(shuffle = true, order = shuffleOrder(sequential(state.queue.size), currentIdx, event.rng), position = 0)
                else -> state.copy(shuffle = false, order = sequential(state.queue.size), position = currentIdx ?: 0)
            }
        }
        YtEvent.Clear -> YtState(urls = state.urls, details = state.details, streams = state.streams, repeat = state.repeat, shuffle = state.shuffle, autoContinue = state.autoContinue)
    }

    /** Sambung antrean lanjutan bila tersedia, lalu mulai putar bila URL sudah ada. */
    fun settle(state: YtState): YtState {
        var s = state
        repeat(8) {
            val (from, songs) = continuationSource(s) ?: return@repeat
            s = reduce(s, YtEvent.ContinueQueue(from, songs))
        }
        return if (s.status == YtStatus.Preparing && s.currentUrl != null) reduce(s, YtEvent.Playing) else s
    }

    // ------------------------------------------------------------------ tampilan

    /** `"6.38"` / `"3:05"` / `"1:02:03"` → detik; teks dari hulu memakai titik (hl=id). */
    fun parseDuration(text: String): Int? {
        val m = Regex("^(\\d+)[:.](\\d{2})(?:[:.](\\d{2}))?$").find(text.trim()) ?: return null
        val parts = if (m.groupValues[3].isEmpty()) listOf(m.groupValues[1], m.groupValues[2]) else listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        return parts.fold(0) { total, p -> total * 60 + p.toInt() }
    }

    fun formatClock(seconds: Long): String {
        val total = maxOf(0L, seconds)
        val s = total % 60
        val m = (total / 60) % 60
        val h = total / 3600
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatDuration(text: String): String = parseDuration(text)?.let { formatClock(it.toLong()) }.orEmpty()

    /** Teks durasi untuk baris: angka server lebih dulu; episode podcast memakai teksnya ("42 mnt"). */
    fun formatDuration(song: Song): String = when {
        song.live -> "LIVE"
        song.durationSeconds != null -> formatClock(song.durationSeconds.toLong())
        else -> parseDuration(song.duration)?.let { formatClock(it.toLong()) } ?: song.duration

    }

    /** URL dianggap kedaluwarsa sedikit lebih awal supaya tidak habis di tengah lagu. */
    const val EXPIRY_MARGIN_MS = 5 * 60_000L

    /** Data antrean yang disimpan supaya bertahan saat proses dimatikan sistem. */
    fun snapshot(s: YtState, positionMs: Long): QueueSnapshot? =
        if (s.queue.isEmpty() || s.status == YtStatus.Idle) null
        else QueueSnapshot(s.queue, s.order, s.position, positionMs, s.repeat, s.shuffle, s.autoContinue, s.radio?.copy(pending = false))

    /** Total durasi antrean yang diketahui (lagu tanpa durasi tidak dijumlahkan, bukan dianggap 0). */
    fun knownTotalSeconds(songs: List<Song>): Pair<Long, Boolean> {
        var total = 0L
        var complete = true
        songs.forEach { s -> val d = s.seconds; if (d == null) complete = false else total += d }
        return total to complete
    }

    fun barStatus(s: YtState, online: Boolean): String = when {
        s.status == YtStatus.Error -> s.error.ifEmpty { "Gagal memuat audio." }
        s.current?.live == true && s.status != YtStatus.Preparing -> listOf("Siaran langsung", s.current?.artist.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
        s.awaitingMore -> if (online) "Mencari lagu lanjutan…" else "Tidak ada koneksi"
        s.status == YtStatus.Preparing -> if (online) s.label.ifEmpty { "Menyiapkan audio…" }
            // Offline tapi label terisi = antrean sedang MENUNGGU jaringan
            // kembali (Agen B), bukan sekadar menyiapkan: tampilkan labelnya.
            else s.label.ifEmpty { "Tidak ada koneksi" }
        s.status == YtStatus.Idle -> if (online) "Cari lagu untuk diputar" else "Tidak ada koneksi"
        else -> listOf(s.current?.artist.orEmpty(), if (s.order.size > 1) "${s.position + 1}/${s.order.size}" else "").filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun progressLabel(percent: Int): String = when {
        percent <= 0 -> "Menghubungi server…"
        percent < 90 -> "Menyiapkan audio… $percent%"
        else -> "Hampir siap…"
    }

    /** Gabung halaman hasil pencarian tanpa duplikat. */
    fun mergePages(existing: List<Song>, incoming: List<Song>): List<Song> {
        val seen = existing.map { it.videoId }.toHashSet()
        return existing + incoming.filter { it.videoId.isNotEmpty() && seen.add(it.videoId) }
    }
}

/**
 * Filter pencarian musik (daftar tertutup Worker). `null` = campuran; kini juga punya halaman
 * lanjutan lewat cursor gabungan Worker, jadi "Muat lagi" berlaku untuk semua filter.
 */
enum class SearchFilter(val value: String?, val label: String) {
    All(null, "Semua"), Songs("songs", "Lagu"), Videos("videos", "Video"), Albums("albums", "Album"), Artists("artists", "Artis"),
    Playlists("playlists", "Playlist"), Podcasts("podcasts", "Podcast"), Episodes("episodes", "Episode");

    val pages: Boolean get() = true
}

/** Antrean yang disimpan lintas proses (tanpa URL: URL direct kedaluwarsa & tidak boleh disimpan lama). */
data class QueueSnapshot(
    val queue: List<Song>,
    val order: List<Int>,
    val position: Int,
    val positionMs: Long,
    val repeat: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val autoContinue: Boolean = true,
    val radio: RadioSource? = null,
)
