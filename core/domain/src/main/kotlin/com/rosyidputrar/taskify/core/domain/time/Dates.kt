package com.rosyidputrar.taskify.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Tanggal lokal disimpan sebagai teks `YYYY-MM-DD` (kolom `date` Postgres) — tidak bergeser zona
 * waktu. Timestamp absolut = ISO UTC. Zona aplikasi = WIB (UTC+7 tanpa DST), sama dengan webapp.
 * Port dari `lib/date.ts`.
 */
object Dates {
    val WIB: ZoneOffset = ZoneOffset.ofHours(7)
    private val ID = Locale.forLanguageTag("id-ID")

    fun parse(date: String): LocalDate = LocalDate.parse(date)

    /** Seperti [parse], tapi mengembalikan null untuk teks tanggal yang rusak (data eksternal). */
    fun parseOrNull(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

    fun today(nowMillis: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(nowMillis).atOffset(WIB).toLocalDate().toString()

    fun addDays(date: String, amount: Long): String = parse(date).plusDays(amount).toString()

    fun diffDays(a: String, b: String): Long = ChronoUnit.DAYS.between(parse(a), parse(b))

    /** Hari dalam minggu, 0 = Senin … 6 = Minggu (konvensi rrule). */
    fun weekdayOf(date: String): Int = parse(date).dayOfWeek.value - 1

    fun monthStart(date: String): String = date.substring(0, 7) + "-01"

    fun monthEnd(date: String): String = parse(date).let { it.withDayOfMonth(it.lengthOfMonth()) }.toString()

    /** 'HH:MM:SS' / 'HH:MM' → 'HH:MM'. */
    fun shortTime(time: String?): String = time?.take(5) ?: ""

    /** Input jam → 'HH:MM:00' (format kolom `time` Postgres). */
    fun toDbTime(time: String?): String? = time?.takeIf { it.isNotBlank() }?.let { it.take(5) + ":00" }

    /**
     * Timestamp dari DB. PowerSync mengirim timestamptz sebagai `2026-10-05 07:00:00Z` (spasi),
     * tulisan lokal memakai ISO dengan `T`. Offset `+00` / `+00:00` juga diterima.
     */
    fun parseTimestampMillis(value: String): Long? = runCatching {
        var v = value.trim().replace(' ', 'T')
        if (Regex("[+-]\\d{2}$").containsMatchIn(v)) v += ":00"
        java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
    }.recoverCatching { Instant.parse(value.trim().replace(' ', 'T')).toEpochMilli() }.getOrNull()

    /** Tanggal + jam WIB → ISO UTC (presisi milidetik). Tanpa jam = 00:00. */
    fun localDateTimeToIso(date: String, time: String? = null): String {
        val t = LocalTime.parse(shortTime(time).ifEmpty { "00:00" })
        val instant = LocalDateTime.of(parse(date), t).toInstant(WIB)
        return isoFormatter.format(instant)
    }

    fun localDateTimeToMillis(date: String, time: String? = null): Long =
        LocalDateTime.of(parse(date), LocalTime.parse(shortTime(time).ifEmpty { "00:00" })).toInstant(WIB).toEpochMilli()

    /** ISO UTC → (tanggal, jam) WIB. */
    fun isoToLocal(value: String): Pair<String, String>? {
        val millis = parseTimestampMillis(value) ?: return null
        val local = Instant.ofEpochMilli(millis).atOffset(WIB)
        return local.toLocalDate().toString() to "%02d:%02d".format(local.hour, local.minute)
    }

    fun millisToIso(millis: Long): String = isoFormatter.format(Instant.ofEpochMilli(millis))

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(date: String, pattern: String): String = DateTimeFormatter.ofPattern(pattern, ID).format(parse(date))

    /** Label relatif: "Hari ini", "Besok", "Kemarin", "Jumat", "Rab, 8 Okt", "8 Okt 2027". */
    fun relativeDayLabel(date: String, today: String): String {
        val diff = diffDays(today, date)
        return when {
            diff == 0L -> "Hari ini"
            diff == 1L -> "Besok"
            diff == -1L -> "Kemarin"
            date.take(4) != today.take(4) -> format(date, "d MMM yyyy")
            diff in 2..6 -> format(date, "EEEE")
            else -> format(date, "EEE, d MMM")
        }
    }

    /** Sapaan berdasarkan jam WIB. */
    fun greeting(nowMillis: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(nowMillis).atOffset(WIB).hour
        return when {
            hour < 4 -> "Selamat malam"
            hour < 11 -> "Selamat pagi"
            hour < 15 -> "Selamat siang"
            hour < 19 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }
}
