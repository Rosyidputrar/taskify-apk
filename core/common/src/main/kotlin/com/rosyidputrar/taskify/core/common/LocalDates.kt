package com.rosyidputrar.taskify.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tanggal tanpa jam disimpan sebagai `YYYY-MM-DD` (tidak bergeser zona waktu).
 * Zona tampilan default WIB, sama dengan webapp.
 */
object LocalDates {
    val WIB: TimeZone = TimeZone.of("Asia/Jakarta")

    fun today(zone: TimeZone = WIB, clock: Clock = Clock.System): LocalDate =
        clock.now().toLocalDateTime(zone).date

    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    fun dateOf(instant: Instant, zone: TimeZone = WIB): LocalDate = instant.toLocalDateTime(zone).date

    private val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    private val monthNames = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    fun dayName(date: LocalDate): String = dayNames[date.dayOfWeek.ordinal]
    fun monthName(monthNumber: Int): String = monthNames[monthNumber - 1]

    /** `Minggu, 15 November 2026` */
    fun long(date: LocalDate): String =
        "${dayName(date)}, ${date.day} ${monthName(date.month.ordinal + 1)} ${date.year}"

    /** `15 Nov` */
    fun short(date: LocalDate): String = "${date.day} ${monthName(date.month.ordinal + 1).take(3)}"
}
