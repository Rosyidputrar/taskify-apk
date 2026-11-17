package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.domain.media.SearchFilter as DomainSearchFilter

/**
 * Filter pencarian InnerTube — nilai `params` mentah (base64) yang dikirim
 * di badan JSON permintaan `search`.
 *
 * Ditulis dari `worker/innertube.ts` SEARCH_FILTERS (repo music-taskify,
 * terverifikasi live 2026-11-16), BUKAN dari ArchiveTune `SearchFilter.kt`:
 * nilai dasarnya sama, tapi ArchiveTune menulisnya URL-encode (`%3D%3D`,
 * karena dipakai di query string) dan tidak punya `podcasts`/`episodes`,
 * sementara filter `playlists`-nya berbeda nilainya
 * (`EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ=` featured vs Worker
 * `EgeKAQQoAEABagoQAxAEEAkQBRAK`). Di badan JSON tidak perlu URL-encode.
 *
 * Catatan: nama kelas sama dengan enum domain
 * (`com.rosyidputrar.taskify.core.domain.media.SearchFilter`) tapi paketnya
 * berbeda — yang domain dipakai UI (`value = "songs"`), yang ini dipakai
 * transport (`params` base64). Pemetaan di [forDomain].
 */
@JvmInline
value class SearchFilter(val value: String) {
    companion object {
        val SONGS = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA==")
        val VIDEOS = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA==")
        val ALBUMS = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA==")
        val ARTISTS = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA==")
        val PLAYLISTS = SearchFilter("EgeKAQQoAEABagoQAxAEEAkQBRAK")
        val PODCASTS = SearchFilter("EgWKAQJQAWoKEAkQBRAKEAMQBA==")
        val EPISODES = SearchFilter("EgWKAQJIAWoKEAkQBRAKEAMQBA==")

        /** Filter domain UI -> filter transport; `All` (campuran) = tanpa `params`. */
        fun forDomain(filter: DomainSearchFilter): SearchFilter? = when (filter) {
            DomainSearchFilter.All -> null
            DomainSearchFilter.Songs -> SONGS
            DomainSearchFilter.Videos -> VIDEOS
            DomainSearchFilter.Albums -> ALBUMS
            DomainSearchFilter.Artists -> ARTISTS
            DomainSearchFilter.Playlists -> PLAYLISTS
            DomainSearchFilter.Podcasts -> PODCASTS
            DomainSearchFilter.Episodes -> EPISODES
        }
    }
}
