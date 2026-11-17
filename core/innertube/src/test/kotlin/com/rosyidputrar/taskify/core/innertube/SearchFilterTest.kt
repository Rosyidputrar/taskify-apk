package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.domain.media.SearchFilter as DomainSearchFilter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Nilai `params` mengikuti `worker/innertube.ts` SEARCH_FILTERS — bukan
 * nilai URL-encode ArchiveTune, dan `playlists`/`podcasts`/`episodes`
 * memakai nilai Worker yang terverifikasi live paling baru.
 */
class SearchFilterTest {
    @Test
    fun nilaiParamsSamaDenganWorker() {
        assertEquals("EgWKAQIIAWoKEAkQBRAKEAMQBA==", SearchFilter.SONGS.value)
        assertEquals("EgWKAQIQAWoKEAkQChAFEAMQBA==", SearchFilter.VIDEOS.value)
        assertEquals("EgWKAQIYAWoKEAkQChAFEAMQBA==", SearchFilter.ALBUMS.value)
        assertEquals("EgWKAQIgAWoKEAkQChAFEAMQBA==", SearchFilter.ARTISTS.value)
        assertEquals("EgeKAQQoAEABagoQAxAEEAkQBRAK", SearchFilter.PLAYLISTS.value)
        assertEquals("EgWKAQJQAWoKEAkQBRAKEAMQBA==", SearchFilter.PODCASTS.value)
        assertEquals("EgWKAQJIAWoKEAkQBRAKEAMQBA==", SearchFilter.EPISODES.value)
    }

    @Test
    fun forDomainMemetakanSemuaFilterDomain() {
        assertNull(SearchFilter.forDomain(DomainSearchFilter.All))
        assertEquals(SearchFilter.SONGS, SearchFilter.forDomain(DomainSearchFilter.Songs))
        assertEquals(SearchFilter.VIDEOS, SearchFilter.forDomain(DomainSearchFilter.Videos))
        assertEquals(SearchFilter.ALBUMS, SearchFilter.forDomain(DomainSearchFilter.Albums))
        assertEquals(SearchFilter.ARTISTS, SearchFilter.forDomain(DomainSearchFilter.Artists))
        assertEquals(SearchFilter.PLAYLISTS, SearchFilter.forDomain(DomainSearchFilter.Playlists))
        assertEquals(SearchFilter.PODCASTS, SearchFilter.forDomain(DomainSearchFilter.Podcasts))
        assertEquals(SearchFilter.EPISODES, SearchFilter.forDomain(DomainSearchFilter.Episodes))
    }
}
