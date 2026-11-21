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

    @Test fun explicitIndonesianAndIsoDates() {
        assertEquals(QuickParse("Cancel pembayaran", "2027-02-06"), p("Cancel pembayaran feb 2027 tgl 6"))
        assertEquals(QuickParse("Cancel pembayaran", "2027-02-06", "09:00"), p("Cancel pembayaran 6 feb 2027 jam 9"))
        assertEquals(QuickParse("Perpanjang domain", "2027-02-06"), p("Perpanjang domain tanggal 6 februari 2027"))
        assertEquals(QuickParse("Audit tahunan", "2027-02-06"), p("Audit tahunan 2027-02-06"))
        assertEquals(QuickParse("Bayar kos", "2027-02-06"), p("Bayar kos 6/2/2027"))
        // Tanpa tahun: 15 Nov masih di tahun ini (today = 7 Okt 2026); 6 Feb sudah lewat → tahun depan (2027).
        assertEquals("2026-11-15", p("Kontrol gigi 15 nov").dueDate)
        assertEquals("2027-02-06", p("Cancel Netflix 6 feb").dueDate)
        // Tanggal tidak valid ditolak aman.
        assertEquals(QuickParse("Tes 31 feb 2027"), p("Tes 31 feb 2027"))
    }

    @Test fun reminderPrefixEnablesDefaultRemindAt() {
        val r = p("Ingatkan aku untuk cancel pembayaran feb 2027 tgl 6")
        assertEquals(QuickParse("cancel pembayaran", "2027-02-06", remind = true), r)
        assertEquals("2027-02-06T02:00:00.000Z", r.defaultRemindAt())

        val withTime = p("Ingatkan bayar cicilan 6 feb 2027 jam 14:30")
        assertEquals(QuickParse("bayar cicilan", "2027-02-06", "14:30", remind = true), withTime)
        assertEquals("2027-02-06T07:30:00.000Z", withTime.defaultRemindAt())

        // Di parseCapture, awalan "ingatkan …" tetap Tugas (bukan transaksi) walau menyebut nominal.
        val cap = com.rosyidputrar.taskify.core.domain.capture.parseCapture("Ingatkan bayar listrik 350rb 6 feb 2027", today)
        assertEquals(com.rosyidputrar.taskify.core.domain.capture.CaptureType.Task, cap.type)
        assertEquals("2027-02-06", cap.date)
        assertEquals(true, cap.remind)
    }
}
