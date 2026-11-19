package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import java.net.URI
import java.net.URLDecoder

/**
 * Pengenal URL YouTube — port `resolveYoutubeUrl` + extractor id dari
 * `src/ytmusic/parse.ts` (Worker). Dipakai untuk link yang ditempel/dibagikan
 * pengguna; digabung dengan `MusicCatalog.findLink/isLink` yang sudah ada
 * di `:core:domain` (jangan duplikasi logika pencari link di sana).
 */

/** Host YouTube yang diterima. */
val MYTUBE_HOSTS = listOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtu.be",
)

fun isYoutubeHost(host: String): Boolean =
    MYTUBE_HOSTS.contains(host.lowercase())

private val ID11 = Regex("""^[a-zA-Z0-9_-]{11}$""")
private val CHANNEL_ID = Regex("""^UC[a-zA-Z0-9_-]{22}$""")

fun extractVideoId(raw: String): String {
    val s = raw.trim()
    if (ID11.matches(s)) return s
    return Regex("""[?&]v=([a-zA-Z0-9_-]{11})""").find(s)?.groupValues?.getOrNull(1)
        ?: Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(s)?.groupValues?.getOrNull(1)
        ?: s
}

fun extractPlaylistId(raw: String): String {
    val s = raw.trim()
    if (s.startsWith("VL")) return s.removePrefix("VL")
    return Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(s)?.groupValues?.getOrNull(1) ?: s
}

fun extractAlbumId(raw: String): String {
    val s = raw.trim()
    if (s.startsWith("MPREb")) return s
    val browse = Regex("""/browse/(MPREb[a-zA-Z0-9_-]+)""").find(s)?.groupValues?.getOrNull(1)
    if (browse != null) return browse
    val olak = Regex("""[?&]list=(OLAK5uy_[a-zA-Z0-9_-]+)""").find(s)?.groupValues?.getOrNull(1)
    if (olak != null) return "VL$olak"
    if (s.startsWith("OLAK5uy_")) return "VL$s"
    return s
}

fun extractChannelId(raw: String): String {
    val s = raw.trim()
    if (CHANNEL_ID.matches(s)) return s
    return Regex("""/channel/(UC[a-zA-Z0-9_-]{22})""").find(s)?.groupValues?.getOrNull(1)
        ?: Regex("""/browse/(UC[a-zA-Z0-9_-]{22})""").find(s)?.groupValues?.getOrNull(1)
        ?: s
}

/** Nilai `list=` yang dikenali sebagai playlist/mix/album: awalan + panjang minimum. */
private val PLAYLIST_LIST = Regex("""^(PL|VL|RD|UU|LL|FL|OLAK5uy_)[a-zA-Z0-9_-]{8,80}$""")

private fun queryParams(uri: URI): Map<String, String> {
    val raw = uri.rawQuery ?: return emptyMap()
    val out = mutableMapOf<String, String>()
    for (pair in raw.split("&")) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val key = pair.substring(0, eq)
        val value = try {
            URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
        } catch (_: IllegalArgumentException) {
            pair.substring(eq + 1)
        }
        if (key !in out) out[key] = value
    }
    return out
}

/**
 * Mengenali URL YouTube yang sudah lolos pemeriksaan host. `null` bila URL
 * itu tidak mengandung id yang dikenali.
 *
 * Urutan penting:
 * 1. `v=` menang atas `list=` (URL "watch + playlist" adalah lagu itu).
 * 2. `list=OLAK5uy_…` adalah album, bukan playlist biasa.
 * 3. `list=RD…` adalah mix radio (`kind = "playlist"`, dibaca lewat radio).
 *
 * Yang SENGAJA tidak dikenali: `/@handle` (butuh lookup nama → browseId),
 * `/live/…` di luar bentuk video, dan `list=` yang terlalu pendek/tanpa
 * awalan dikenal.
 */
fun resolveYoutubeUrl(rawUrl: String): ResolvedLink? {
    val uri = try {
        URI(rawUrl.trim())
    } catch (_: Exception) {
        return null
    }
    val host = (uri.host ?: "").lowercase()
    if (!isYoutubeHost(host)) return null
    val params = queryParams(uri)
    val path = uri.path ?: ""

    // 1. Video: ?v=… di host mana pun, lalu bentuk path /shorts|embed|v|live.
    val v = params["v"]
    if (v != null && ID11.matches(v)) {
        return ResolvedLink("video", videoId = v, url = "https://music.youtube.com/watch?v=$v")
    }
    val pathVideo = Regex("""^/(shorts|embed|v|live)/([a-zA-Z0-9_-]{11})(?:/|$)""")
        .find(path)?.groupValues?.getOrNull(2)
    if (pathVideo != null) {
        return ResolvedLink("video", videoId = pathVideo, url = "https://music.youtube.com/watch?v=$pathVideo")
    }
    // youtu.be/<id> — parameter tambahan (?t=, ?si=, ?list=) diabaikan.
    if (host == "youtu.be") {
        val id = path.removePrefix("/").split("/").firstOrNull().orEmpty()
        if (ID11.matches(id)) {
            return ResolvedLink("video", videoId = id, url = "https://music.youtube.com/watch?v=$id")
        }
    }

    // 2. Playlist / album / mix radio dari ?list=…
    val list = params["list"]
    if (list != null && PLAYLIST_LIST.matches(list)) {
        if (list.startsWith("OLAK5uy_")) {
            return ResolvedLink(
                kind = "album",
                browseId = "VL$list",
                playlistId = list,
                url = "https://music.youtube.com/playlist?list=$list",
            )
        }
        val playlistId = if (list.startsWith("VL")) list.removePrefix("VL") else list
        return ResolvedLink(
            kind = "playlist",
            playlistId = playlistId,
            url = "https://music.youtube.com/playlist?list=$list",
        )
    }

    // 3. Album dari /browse/MPREb… .
    val browseAlbum = Regex("""^/browse/(MPREb[a-zA-Z0-9_-]+)$""").find(path)?.groupValues?.getOrNull(1)
    if (browseAlbum != null) {
        return ResolvedLink(
            kind = "album",
            browseId = browseAlbum,
            url = "https://music.youtube.com/browse/$browseAlbum",
        )
    }

    // 3b. Acara podcast dari /browse/MPSP… .
    val browsePodcast = Regex("""^/browse/(MPSP[a-zA-Z0-9_-]+)$""").find(path)?.groupValues?.getOrNull(1)
    if (browsePodcast != null) {
        return ResolvedLink(
            kind = "podcast",
            browseId = browsePodcast,
            url = "https://music.youtube.com/browse/$browsePodcast",
        )
    }

    // 4. Artis dari /channel/UC… atau /browse/UC… .
    val channelId = extractChannelId(rawUrl.trim())
    if (channelId != rawUrl.trim()) {
        return ResolvedLink(
            kind = "artist",
            browseId = channelId,
            url = "https://music.youtube.com/channel/$channelId",
        )
    }

    return null
}
