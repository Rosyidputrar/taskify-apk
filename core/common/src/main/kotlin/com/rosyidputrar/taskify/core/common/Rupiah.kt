package com.rosyidputrar.taskify.core.common

import kotlin.math.absoluteValue

/**
 * Uang selalu integer rupiah (sama dengan skema server) — tidak pernah Float/Double.
 */
object Rupiah {
    /** `1250000` → `Rp 1.250.000`; negatif → `−Rp 1.250.000`. */
    fun format(amount: Long, withSymbol: Boolean = true): String {
        val digits = groupThousands(amount.absoluteValue)
        val body = if (withSymbol) "Rp $digits" else digits
        return if (amount < 0) "−$body" else body
    }

    /** Format ringkas untuk chip & grafik: `1,2 jt`, `350 rb`. */
    fun compact(amount: Long): String {
        val abs = amount.absoluteValue
        val sign = if (amount < 0) "−" else ""
        return when {
            abs >= 1_000_000_000 -> sign + oneDecimal(abs, 1_000_000_000) + " M"
            abs >= 1_000_000 -> sign + oneDecimal(abs, 1_000_000) + " jt"
            abs >= 1_000 -> sign + oneDecimal(abs, 1_000) + " rb"
            else -> sign + abs.toString()
        }
    }

    private val SUFFIX = mapOf(
        "rb" to 1_000L, "ribu" to 1_000L, "k" to 1_000L,
        "jt" to 1_000_000L, "juta" to 1_000_000L, "m" to 1_000_000L,
        "mil" to 1_000_000_000L, "miliar" to 1_000_000_000L,
    )

    /**
     * Membaca input bebas ala Indonesia → integer rupiah (port 1:1 `parseIDR` webapp):
     * `25000`, `25.000`, `Rp 25.000`, `25rb`, `25k`, `1,5jt`, `1.5 juta`, `2m` (= 2 juta).
     * Tanpa sufiks, titik/koma = pemisah ribuan; dengan sufiks, titik/koma TERAKHIR = desimal.
     */
    fun parse(input: String): Long? {
        val raw = input.trim().lowercase().replace(Regex("^rp\\.?\\s*"), "")
        val match = Regex("^([\\d.,\\s]+)\\s*([a-z]*)$").find(raw) ?: return null
        val numPart = match.groupValues[1]
        val suffix = match.groupValues[2]
        val multiplier = if (suffix.isEmpty()) 1L else SUFFIX[suffix] ?: return null
        var normalized = numPart.replace(Regex("\\s"), "")
        if (multiplier == 1L) {
            normalized = normalized.replace(".", "").replace(",", "")
        } else {
            normalized = normalized.replace(',', '.').let { n ->
                val last = n.lastIndexOf('.')
                if (last == -1) n else n.substring(0, last).replace(".", "") + "." + n.substring(last + 1)
            }
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.multiply(multiplier.toBigDecimal()).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** True bila input tampak sedang diketik menuju sufiks valid ("25r" → "25rb"): jangan tampilkan error dulu. */
    fun isIncomplete(input: String): Boolean {
        val raw = input.trim().lowercase().replace(Regex("^rp\\.?\\s*"), "")
        val typed = Regex("^[\\d.,\\s]*\\d[\\d.,\\s]*\\s*([a-z]+)$").find(raw)?.groupValues?.get(1) ?: return false
        return SUFFIX.keys.any { it != typed && it.startsWith(typed) }
    }

    private fun groupThousands(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(".").reversed()

    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = (value * 10 + unit / 2) / unit
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) whole.toString() else "$whole,$frac"
    }
}
