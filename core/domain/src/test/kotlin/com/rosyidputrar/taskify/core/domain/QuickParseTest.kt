package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.tasks.QuickParse
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import kotlin.test.Test
import kotlin.test.assertEquals

/** Port dari quick-parse.test.ts webapp — hasil harus identik. */
class QuickParseTest {
    private val today = "2026-10-07" // Rabu

    private fun p(s: String, t: String = today) = parseQuickAdd(s, t).copy(tokens = emptyList())

    @Test fun relativeDateTimeAndPriority() {
        assertEquals(QuickParse("Bayar listrik", "2026-10-08", "09:00", 3), p("Bayar listrik besok jam 9 !3"))
    }

    @Test fun namedDayIsNextOccurrence() {
        assertEquals(QuickParse("Rapat", "2026-10-12", "14:30"), p("Rapat senin 14.30"))
        assertEquals("2026-10-09", p("Kirim laporan jumat").dueDate)
        assertEquals("2026-10-11", p("Beres-beres minggu").dueDate)
    }

    @Test fun timeWithoutDateIsTodayAndTagsAreTaken() {
        assertEquals(QuickParse("Telepon klien", today, "16:00", tags = listOf("kerja")), p("Telepon klien 16.00 #kerja"))
    }

    @Test fun emptyTitleKeepsInput() = assertEquals(QuickParse("besok"), p("besok"))

    @Test fun nextWeek() = assertEquals(QuickParse("Review", "2026-10-14"), p("Review minggu depan"))

    @Test fun dayNameDepan() {
        assertEquals(QuickParse("Rapat", "2026-10-12"), p("Rapat senin depan"))
        assertEquals("Rapat", p("Rapat kamis depan").title)
        assertEquals("2026-10-12", p("Rapat senin depan", "2026-10-05").dueDate)
    }

    @Test fun dayNameIni() {
        assertEquals(QuickParse("Rapat", "2026-10-08", "10:00"), p("Rapat kamis ini jam 10"))
        assertEquals(QuickParse("Rapat", "2026-10-09"), p("Rapat jumat ini"))
        assertEquals(QuickParse("futsal", "2026-10-10"), p("Sabtu ini futsal"))
        assertEquals(today, p("Rapat rabu ini").dueDate)
        assertEquals("2026-10-12", p("Rapat senin ini").dueDate)
        assertEquals("2026-10-11", p("Beres-beres ahad ini").dueDate)
    }

    @Test fun mingguIniThenDayName() {
        assertEquals(QuickParse("Rapat minggu ini", "2026-10-09"), p("Rapat minggu ini jumat"))
        assertEquals("2026-10-08", p("Laporan minggu ini kamis").dueDate)
    }

    @Test fun weekPhrasesStayInTitle() {
        listOf("Baca minggu ini", "Review minggu lalu", "Laporan minggu ke-2", "Bab minggu 3").forEach {
            assertEquals(QuickParse(it), p(it), it)
        }
    }

    @Test fun versionsAndPricesAreNotTimes() {
        assertEquals(QuickParse("Update ke versi 3.14", today), p("Update ke versi 3.14 hari ini"))
        assertEquals(QuickParse("Beli beras 10.50"), p("Beli beras 10.50"))
        assertEquals(QuickParse("Baca bab 2.30 nanti"), p("Baca bab 2.30 nanti"))
    }

    @Test fun markersMakeAnyNumberATime() {
        assertEquals("03:14", p("Jemput jam 3.14").dueTime)
        assertEquals("17:45", p("Sholat pukul 17:45").dueTime)
    }

    @Test fun colonAlwaysTimeDotOnlyQuarterHours() {
        assertEquals("09:05", p("Standup 9:05").dueTime)
        assertEquals("19:15", p("Makan malam 19.15").dueTime)
        assertEquals("19:00", p("Makan malam 19.00").dueTime)
    }

    @Test fun invalidTimeRejected() = assertEquals(QuickParse("Tiket 25:99"), p("Tiket 25:99"))

    @Test fun recognisedTokensAreReported() {
        assertEquals(listOf("#kerja", "!2", "jam 9", "besok"), parseQuickAdd("Rapat besok jam 9 !2 #kerja", today).tokens)
    }
}
