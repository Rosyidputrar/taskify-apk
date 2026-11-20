package com.rosyidputrar.taskify.core.domain.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutTest {
    @Test fun defaultShowsTheOriginalHome() {
        val d = HomeLayout.Default
        assertEquals(HomeSection.entries, d.visibleSections)
        assertEquals(HomeLayout.DEFAULT_TILES, d.visibleTiles)
        assertTrue(HomeTile.Balance in d.hiddenTiles, "ubin baru opsional")
    }

    @Test fun moveToggleAndRoundTrip() {
        val l = HomeLayout.Default
            .moveSection(HomeSection.Activity, -4)
            .toggleSection(HomeSection.Week)
            .toggleTile(HomeTile.Balance)
            .moveTile(HomeTile.Balance, -10)
        assertEquals(listOf(HomeSection.Activity, HomeSection.Focus, HomeSection.Shortcuts, HomeSection.Summary), l.visibleSections)
        assertEquals(HomeTile.Balance, l.visibleTiles.first())
        assertEquals(l, HomeLayout.decode(l.encode()))
    }

    @Test fun moveIsClampedAtTheEdges() {
        assertEquals(HomeLayout.Default.sections, HomeLayout.Default.moveSection(HomeSection.Week, -1).sections)
        assertEquals(HomeSection.Week, HomeLayout.Default.moveSection(HomeSection.Week, 99).sections.last())
    }

    @Test fun decodingIsForgiving() {
        assertEquals(HomeLayout.Default, HomeLayout.decode(null))
        assertEquals(HomeLayout.Default, HomeLayout.decode(""))
        // Nilai asing & duplikat dibuang; bagian yang hilang (versi lama) ditambahkan dan tampil.
        val old = HomeLayout.decode("Focus,Hapus,-Week,Focus|Mood,-Tasks,Ngawur")
        assertEquals(listOf(HomeSection.Focus, HomeSection.Shortcuts, HomeSection.Summary, HomeSection.Activity, HomeSection.Week), old.sections)
        assertEquals(setOf(HomeSection.Week), old.hiddenSections)
        assertEquals(HomeTile.Mood, old.visibleTiles.first())
        assertTrue(HomeTile.Tasks in old.hiddenTiles)
        assertTrue(HomeTile.Income in old.hiddenTiles, "ubin baru tetap tersembunyi sampai dipilih")
    }

    @Test fun pintasanFromAnOlderSaveLandsRightBelowFocus() {
        // Susunan tersimpan sebelum ada Pintasan (urutan bawaan lama, Aktivitas dipindah ke atas).
        val old = HomeLayout.decode("Activity,Week,Focus,Summary|Tasks,Mood")
        assertEquals(listOf(HomeSection.Activity, HomeSection.Week, HomeSection.Focus, HomeSection.Shortcuts, HomeSection.Summary), old.sections)
        assertTrue(old.showRecent, "Lanjutkan otomatis aktif bawaan")
    }

    @Test fun recentToggleRoundTrips() {
        val l = HomeLayout.Default.toggleRecent()
        assertEquals(false, l.showRecent)
        assertEquals(l, HomeLayout.decode(l.encode()))
        assertEquals(true, HomeLayout.decode(HomeLayout.Default.encode()).showRecent)
    }
}
