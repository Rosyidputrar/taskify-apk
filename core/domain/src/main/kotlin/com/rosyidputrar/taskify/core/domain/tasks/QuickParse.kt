package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * Parser input cepat berbahasa Indonesia — port 1:1 dari `features/tasks/quick-parse.ts`.
 *   "Bayar listrik besok jam 9 !3" → judul "Bayar listrik", besok, 09:00, prioritas 3
 *   "Rapat senin 14.30"            → Senin berikutnya, 14:30
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
)

private val DAY_NAMES = mapOf(
    "senin" to 0, "selasa" to 1, "rabu" to 2, "kamis" to 3, "jumat" to 4, "jum'at" to 4,
    "sabtu" to 5, "minggu" to 6, "ahad" to 6,
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
private val TODAY_RE = Regex("""\shari ini(?=\s)""", RegexOption.IGNORE_CASE)
private val TOMORROW_RE = Regex("""\sbesok(?=\s)""", RegexOption.IGNORE_CASE)
private val DAY_AFTER_RE = Regex("""\slusa(?=\s)""", RegexOption.IGNORE_CASE)
private val NEXT_WEEK_RE = Regex("""\sminggu\s+depan(?=\s)""", RegexOption.IGNORE_CASE)
private val WEEKDAY_RE = Regex(
    """\s(?:hari\s+)?(senin|selasa|rabu|kamis|jum'?at|sabtu|ahad|minggu(?!\s+(?:ini|lalu|kemarin|\d|ke[-\s]?\d)))(\s+(?:depan|ini))?(?=\s)(?!\s+(?:lalu|kemarin|ke[-\s]?\d))""",
    RegexOption.IGNORE_CASE,
)

fun parseQuickAdd(input: String, today: String): QuickParse {
    var text = " ${input.trim()} "
    var dueDate: String? = null
    var dueTime: String? = null
    var priority: Int? = null
    val tokens = mutableListOf<String>()

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

    take(TODAY_RE) { dueDate = today; true }
    take(TOMORROW_RE) { dueDate = Dates.addDays(today, 1); true }
    take(DAY_AFTER_RE) { dueDate = Dates.addDays(today, 2); true }
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
        QuickParse(title, dueDate, dueTime, priority, tags, tokens)
    } else {
        QuickParse(input.trim())
    }
}
