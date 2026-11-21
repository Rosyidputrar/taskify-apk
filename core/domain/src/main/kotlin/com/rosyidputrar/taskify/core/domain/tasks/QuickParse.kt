package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.LocalDate

/**
 * Parser input cepat berbahasa Indonesia — port 1:1 dari `features/tasks/quick-parse.ts`,
 * diperluas untuk tanggal eksplisit ("6 feb 2027", "feb 2027 tgl 6") & pemicu pengingat ("ingatkan …").
 *   "Bayar listrik besok jam 9 !3" → judul "Bayar listrik", besok, 09:00, prioritas 3
 *   "Rapat senin 14.30"            → Senin berikutnya, 14:30
 *   "Ingatkan cancel Netflix 6 feb 2027" → judul "cancel Netflix", 2027-02-06, pengingat otomatis
 * Token yang dikenali dihapus dari judul; judul kosong → input dipakai apa adanya.
 */
data class QuickParse(
    val title: String,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    /** Potongan teks yang dikenali — untuk menyorot token di kotak input. */
    val tokens: List<String> = emptyList(),
    /** Pengguna menulis pemicu pengingat ("ingatkan …", "remind …") → pasang alarm otomatis. */
    val remind: Boolean = false,
) {
    /** Hitung `remind_at` ISO UTC bawaan bila [remind] aktif dan ada tanggal. */
    fun defaultRemindAt(effectiveDueDate: String? = dueDate): String? {
        if (!remind || effectiveDueDate == null) return null
        return if (dueTime != null) {
            computeRemindAt(ReminderPreset.AtTime, effectiveDueDate, dueTime)
        } else {
            computeRemindAt(ReminderPreset.Morning, effectiveDueDate, null)
        }
    }
}

private val DAY_NAMES = mapOf(
    "senin" to 0, "selasa" to 1, "rabu" to 2, "kamis" to 3, "jumat" to 4, "jum'at" to 4,
    "sabtu" to 5, "minggu" to 6, "ahad" to 6,
)

private val MONTH_NAMES = mapOf(
    "jan" to 1, "januari" to 1, "january" to 1,
    "feb" to 2, "februari" to 2, "february" to 2, "peb" to 2, "pebruari" to 2,
    "mar" to 3, "maret" to 3, "march" to 3,
    "apr" to 4, "april" to 4,
    "mei" to 5, "may" to 5,
    "jun" to 6, "juni" to 6, "june" to 6,
    "jul" to 7, "juli" to 7, "july" to 7,
    "agu" to 8, "agst" to 8, "agust" to 8, "agustus" to 8, "aug" to 8, "august" to 8,
    "sep" to 9, "sept" to 9, "september" to 9,
    "okt" to 10, "oktober" to 10, "oct" to 10, "october" to 10,
    "nov" to 11, "november" to 11, "nop" to 11, "nopember" to 11,
    "des" to 12, "desember" to 12, "dec" to 12, "december" to 12,
)

private val MONTH_ALT = MONTH_NAMES.keys.sortedByDescending { it.length }.joinToString("|")

/** Pemicu pengingat di awal kalimat: "ingatkan (aku/saya) (untuk) …", "ingetin …", "remind (me) (to) …". */
internal val REMIND_PREFIX_RE = Regex(
    """^\s*(?:ingatkan|ingetin|remind(?:\s+me)?|pengingat)\s*:?\s+(?:(?:aku|saya|gue|gw)\s+)?(?:(?:untuk|buat|to)\s+)?""",
    RegexOption.IGNORE_CASE,
)

/** Menit yang masuk akal untuk jam bertitik tanpa penanda ("14.15"). */
private val DOT_MINUTES = setOf("00", "15", "30", "45")

/** Kata sebelum "N.NN" yang menandakan itu bukan jam (versi, bab, harga, …). */
private val NOT_A_CLOCK = setOf(
    "versi", "ver", "v", "bab", "pasal", "ayat", "hal", "halaman", "no", "nomor", "seri", "bagian",
    "langkah", "step", "lantai", "rp", "ios", "android", "python", "node", "php",
)

private val TAG_RE = Regex("""\s#([\p{L}\p{N}_-]+)""")
private val PRIORITY_RE = Regex("""\s!([1-3])(?=\s)""")
private val MARKED_TIME_RE = Regex("""\s(?:jam|pukul|pkl\.?)\s+(\d{1,2})(?:[.:](\d{2}))?(?=\s)""", RegexOption.IGNORE_CASE)
private val BARE_TIME_RE = Regex("""\s(\d{1,2})([.:])(\d{2})(?=\s)""")
private val ISO_DATE_RE = Regex("""\s(\d{4})-(\d{2})-(\d{2})(?=\s)""")
private val DMY_DATE_RE = Regex("""\s(?:(?:tgl\.?|tanggal)\s+)?(\d{1,2})[/-](\d{1,2})[/-](\d{4})(?=\s)""", RegexOption.IGNORE_CASE)
private val DAY_MONTH_RE = Regex("""\s(?:(?:tgl\.?|tanggal)\s+)?(\d{1,2})\s+($MONTH_ALT)(?:\s+(\d{4}))?(?=\s)""", RegexOption.IGNORE_CASE)
private val MONTH_DAY_RE = Regex("""\s($MONTH_ALT)(?:\s+(\d{4}))?\s+(?:(?:tgl\.?|tanggal)\s+)?(\d{1,2})(?:\s+(\d{4}))?(?=\s)""", RegexOption.IGNORE_CASE)
private val TODAY_RE = Regex("""\shari ini(?=\s)""", RegexOption.IGNORE_CASE)
private val TOMORROW_RE = Regex("""\sbesok(?=\s)""", RegexOption.IGNORE_CASE)
private val DAY_AFTER_RE = Regex("""\slusa(?=\s)""", RegexOption.IGNORE_CASE)
private val NEXT_WEEK_RE = Regex("""\sminggu\s+depan(?=\s)""", RegexOption.IGNORE_CASE)
private val WEEKDAY_RE = Regex(
    """\s(?:hari\s+)?(senin|selasa|rabu|kamis|jum'?at|sabtu|ahad|minggu(?!\s+(?:ini|lalu|kemarin|\d|ke[-\s]?\d)))(\s+(?:depan|ini))?(?=\s)(?!\s+(?:lalu|kemarin|ke[-\s]?\d))""",
    RegexOption.IGNORE_CASE,
)

private fun resolveExplicitDate(day: Int, month: Int, explicitYear: Int?, today: String): String? {
    val todayDate = Dates.parseOrNull(today) ?: return null
    if (explicitYear != null) {
        if (explicitYear !in 1970..2100) return null
        return runCatching { LocalDate.of(explicitYear, month, day).toString() }.getOrNull()
    }
    val thisYear = runCatching { LocalDate.of(todayDate.year, month, day) }.getOrNull()
    if (thisYear != null && !thisYear.isBefore(todayDate)) return thisYear.toString()
    // Tanggal tahun ini sudah lewat (atau 29 Feb bukan kabisat): cari tahun terdekat berikutnya.
    for (offset in 1..4) {
        val candidate = runCatching { LocalDate.of(todayDate.year + offset, month, day) }.getOrNull()
        if (candidate != null) return candidate.toString()
    }
    return null
}

fun parseQuickAdd(input: String, today: String): QuickParse {
    var trimmed = input.trim()
    var remind = false
    val tokens = mutableListOf<String>()
    REMIND_PREFIX_RE.find(trimmed)?.let { m ->
        val rest = trimmed.substring(m.range.last + 1).trim()
        if (rest.isNotEmpty()) {
            remind = true
            tokens += m.value.trim()
            trimmed = rest
        }
    }

    var text = " $trimmed "
    var dueDate: String? = null
    var dueTime: String? = null
    var priority: Int? = null

    fun take(re: Regex, fn: (MatchResult) -> Boolean) {
        val m = re.find(text) ?: return
        if (fn(m)) {
            tokens += m.value.trim()
            text = text.replaceRange(m.range, " ")
        }
    }

    fun setTime(h: String?, m: String?): Boolean {
        val hour = h?.toIntOrNull() ?: return false
        val minute = m?.toIntOrNull() ?: 0
        if (hour > 23 || minute > 59) return false
        dueTime = "%02d:%02d".format(hour, minute)
        return true
    }

    // Tag: #kerja
    val tags = mutableListOf<String>()
    text = TAG_RE.replace(text) { m ->
        tags += m.groupValues[1].lowercase()
        tokens += m.value.trim()
        " "
    }

    take(PRIORITY_RE) { priority = it.groupValues[1].toInt(); true }

    // Jam berpenanda: angka apa pun setelah "jam/pukul" adalah jam.
    take(MARKED_TIME_RE) { setTime(it.groupValues[1], it.groupValues[2].ifEmpty { null }) }

    // Jam tanpa penanda: "14:30" pasti jam; "14.30" hanya bila menit kelipatan 15 & bukan versi/harga.
    if (dueTime == null) {
        BARE_TIME_RE.find(text)?.let { m ->
            val prev = text.substring(0, m.range.first).trim().split(Regex("\\s+")).lastOrNull()?.lowercase() ?: ""
            val plausible = m.groupValues[2] == ":" || (m.groupValues[3] in DOT_MINUTES && prev !in NOT_A_CLOCK)
            if (plausible && setTime(m.groupValues[1], m.groupValues[3])) {
                tokens += m.value.trim()
                text = text.replaceRange(m.range, " ")
            }
        }
    }

    // Tanggal eksplisit: "2027-02-06", "6/2/2027", "6 feb 2027", "feb 2027 tgl 6".
    take(ISO_DATE_RE) { m ->
        val resolved = resolveExplicitDate(
            day = m.groupValues[3].toIntOrNull() ?: return@take false,
            month = m.groupValues[2].toIntOrNull() ?: return@take false,
            explicitYear = m.groupValues[1].toIntOrNull(),
            today = today,
        ) ?: return@take false
        dueDate = resolved
        true
    }
    if (dueDate == null) {
        take(DMY_DATE_RE) { m ->
            val resolved = resolveExplicitDate(
                day = m.groupValues[1].toIntOrNull() ?: return@take false,
                month = m.groupValues[2].toIntOrNull() ?: return@take false,
                explicitYear = m.groupValues[3].toIntOrNull(),
                today = today,
            ) ?: return@take false
            dueDate = resolved
            true
        }
    }
    if (dueDate == null) {
        take(DAY_MONTH_RE) { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@take false
            val month = MONTH_NAMES[m.groupValues[2].lowercase()] ?: return@take false
            val year = m.groupValues[3].toIntOrNull()
            val resolved = resolveExplicitDate(day, month, year, today) ?: return@take false
            dueDate = resolved
            true
        }
    }
    if (dueDate == null) {
        take(MONTH_DAY_RE) { m ->
            val month = MONTH_NAMES[m.groupValues[1].lowercase()] ?: return@take false
            val year = m.groupValues[2].toIntOrNull() ?: m.groupValues[4].toIntOrNull()
            val day = m.groupValues[3].toIntOrNull() ?: return@take false
            val resolved = resolveExplicitDate(day, month, year, today) ?: return@take false
            dueDate = resolved
            true
        }
    }

    if (dueDate == null) take(TODAY_RE) { dueDate = today; true }
    if (dueDate == null) take(TOMORROW_RE) { dueDate = Dates.addDays(today, 1); true }
    if (dueDate == null) take(DAY_AFTER_RE) { dueDate = Dates.addDays(today, 2); true }
    // "minggu depan" = pekan depan, BUKAN hari Minggu.
    if (dueDate == null) take(NEXT_WEEK_RE) { dueDate = Dates.addDays(today, 7); true }
    if (dueDate == null) {
        take(WEEKDAY_RE) { m ->
            val target = DAY_NAMES[m.groupValues[1].lowercase()] ?: return@take false
            val sameDayOk = m.groupValues[2].trim().lowercase() == "ini"
            val raw = (target - Dates.weekdayOf(today) + 7) % 7
            val diff = if (raw == 0) (if (sameDayOk) 0 else 7) else raw
            dueDate = Dates.addDays(today, diff.toLong())
            true
        }
    }

    if (dueTime != null && dueDate == null) dueDate = today

    val title = text.replace(Regex("\\s+"), " ").trim()
    return if (title.isNotEmpty()) {
        QuickParse(title, dueDate, dueTime, priority, tags, tokens, remind = remind)
    } else {
        QuickParse(input.trim())
    }
}
