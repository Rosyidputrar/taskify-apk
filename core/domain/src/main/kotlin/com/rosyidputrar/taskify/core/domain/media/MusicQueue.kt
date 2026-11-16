package com.rosyidputrar.taskify.core.domain.media

import kotlin.random.Random

/**
 * Pemutar musik — port `queue-core.ts`, `queue-settle.ts`, `search-core.ts`, `music-view.ts`
 * webapp. Musik tidak menyentuh database: semua keadaan hidup di memori selama sesi.
 */
data class Thumb(val url: String, val width: Int, val height: Int)

data class Song(val videoId: String, val title: String, val artist: String, val duration: String, val thumbnails: List<Thumb>, val url: String = "") {
    /** Gambar terkecil (daftar) & terbesar (sampul). */
    val thumb: String get() = thumbnails.firstOrNull()?.url.orEmpty()
    val cover: String get() = thumbnails.filter { it.url.isNotEmpty() }.maxByOrNull { it.width }?.url.orEmpty()
}

data class Track(val videoId: String, val lyricsId: String, val upNext: List<Song>)

data class TrackDetails(val lyricsId: String, val upNext: List<Song>)

/** Asal audio hasil `/v1/stream/init` (atau poll): direct/R2/konverter + jenis audionya. */
data class StreamSource(val source: String, val mimeType: String?, val itag: Int?) {
    /** Jalur direct yang memilih opus (itag 251) — perlu dilacak, bisa gagal di perangkat tertentu. */
    val isOpus: Boolean get() = mimeType?.contains("opus", ignoreCase = true) == true
}

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
) {
    val current: Song? get() = order.getOrNull(position)?.let { queue.getOrNull(it) }
    val currentUrl: String? get() = current?.let { urls[it.videoId] }
    val lyricsId: String get() = current?.let { details[it.videoId]?.lyricsId }.orEmpty()
    /** Lagu sesuai urutan putar (memperhitungkan acak). */
    val ordered: List<Song> get() = order.mapNotNull { queue.getOrNull(it) }
}

sealed interface YtEvent {
    data class SetQueue(val songs: List<Song>, val startAt: Int = 0, val rng: Random = Random.Default) : YtEvent
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
    data class Error(val message: String) : YtEvent
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

    /** Lagu berikut yang perlu disiapkan lebih dulu (supaya pergantian lagu tidak menunggu konversi). */
    fun prefetchTarget(s: YtState): String? {
        if (s.status != YtStatus.Playing && s.status != YtStatus.Paused) return null
        val pos = if (s.repeat == RepeatMode.One) null else nextIgnoringRepeatOne(s)
        val song = pos?.let { s.order.getOrNull(it) }?.let { s.queue.getOrNull(it) } ?: return null
        if (s.urls.containsKey(song.videoId) || song.videoId in s.prefetching) return null
        return song.videoId
    }

    /** Lagu yang keterangannya (lirik + antrean lanjutan) perlu diambil. */
    fun detailsTarget(s: YtState): String? {
        if (s.status != YtStatus.Playing && s.status != YtStatus.Paused) return null
        val song = s.current ?: return null
        if (s.details.containsKey(song.videoId) || song.videoId in s.detailsPending) return null
        return song.videoId
    }

    /** Antrean lanjutan dari hulu bila lagu terakhir sedang diputar. */
    fun continuationSource(s: YtState): Pair<String, List<Song>>? {
        if (!s.autoContinue || s.repeat != RepeatMode.Off) return null
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
            if (songs.isEmpty()) YtState(urls = state.urls, details = state.details)
            else {
                val startAt = event.startAt.coerceIn(0, songs.size - 1)
                val order = if (state.shuffle) shuffleOrder(sequential(songs.size), startAt, event.rng) else sequential(songs.size)
                state.copy(queue = songs, order = order, position = if (state.shuffle) 0 else startAt, status = YtStatus.Preparing,
                    percent = 0, label = "", error = "", prefetching = emptyList(), continued = emptyList())
            }
        }
        YtEvent.Preparing -> state.copy(status = YtStatus.Preparing, percent = 0, label = "", error = "")
        is YtEvent.Progress -> if (state.status != YtStatus.Preparing) state else state.copy(percent = event.percent, label = event.label)
        is YtEvent.Ready -> state.copy(urls = state.urls + (event.videoId to event.url), percent = 100, label = "")
        is YtEvent.StreamInfo -> state.copy(streams = state.streams + (event.videoId to event.info))
        YtEvent.Playing -> state.copy(status = YtStatus.Playing, error = "", percent = 100, label = "", awaitingMore = false)
        YtEvent.Paused -> if (state.status == YtStatus.Playing) state.copy(status = YtStatus.Paused) else state
        YtEvent.Ended -> {
            val pos = nextPosition(state)
            if (pos == null) state.copy(status = YtStatus.Paused, awaitingMore = state.autoContinue && state.repeat == RepeatMode.Off)
            else {
                val same = pos == state.position
                state.copy(position = pos, status = if (same && state.currentUrl != null) YtStatus.Playing else YtStatus.Preparing, percent = 0, label = "")
            }
        }
        YtEvent.Next, YtEvent.Prev -> {
            val pos = if (event == YtEvent.Next) nextPosition(state, manual = true) else prevPosition(state)
            if (pos == null) state else state.copy(position = pos, status = YtStatus.Preparing, percent = 0, label = "", error = "", awaitingMore = false)
        }
        is YtEvent.Jump -> if (event.position !in state.order.indices) state
            else state.copy(position = event.position, status = YtStatus.Preparing, percent = 0, label = "", error = "")
        is YtEvent.Error -> state.copy(status = YtStatus.Error, error = event.message, percent = 0, label = "")
        is YtEvent.PrefetchStart -> if (event.videoId in state.prefetching) state else state.copy(prefetching = state.prefetching + event.videoId)
        is YtEvent.PrefetchDone -> state.copy(urls = state.urls + (event.videoId to event.url), prefetching = state.prefetching - event.videoId)
        is YtEvent.PrefetchFailed -> state.copy(prefetching = state.prefetching - event.videoId)
        is YtEvent.PrefetchCancelled -> state.copy(prefetching = state.prefetching - event.videoId)
        is YtEvent.DetailsStart -> if (event.videoId in state.detailsPending) state else state.copy(detailsPending = state.detailsPending + event.videoId)
        is YtEvent.DetailsDone -> state.copy(details = state.details + (event.videoId to event.details), detailsPending = state.detailsPending - event.videoId)
        is YtEvent.DetailsFailed -> state.copy(
            details = state.details + (event.videoId to TrackDetails("", emptyList())),
            detailsPending = state.detailsPending - event.videoId, awaitingMore = false,
        )
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
        YtEvent.Clear -> YtState(urls = state.urls, details = state.details, repeat = state.repeat, shuffle = state.shuffle, autoContinue = state.autoContinue)
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

    fun barStatus(s: YtState, online: Boolean): String = when {
        s.status == YtStatus.Error -> s.error.ifEmpty { "Gagal memuat audio." }
        s.awaitingMore -> if (online) "Mencari lagu lanjutan…" else "Tidak ada koneksi"
        s.status == YtStatus.Preparing -> if (online) s.label.ifEmpty { "Menyiapkan audio…" } else "Tidak ada koneksi"
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

/** Filter pencarian musik (daftar tertutup Worker). `null` = campuran, tanpa halaman lanjutan. */
enum class SearchFilter(val value: String?, val label: String) {
    Songs("songs", "Lagu"), Videos("videos", "Video"), All(null, "Semua");

    val pages: Boolean get() = value != null
}
