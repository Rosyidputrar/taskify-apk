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

    /**
     * Membaca input pengguna: `25rb`, `1,5jt`, `Rp 50.000`, `2.5 juta`, `75000`.
     * Mengembalikan null bila tidak ada angka yang valid.
     */
    fun parse(input: String): Long? {
        val text = input.trim().lowercase().removePrefix("rp").trim()
        val match = Regex("""^([0-9][0-9.,]*)\s*(rb|ribu|k|jt|juta|m|miliar)?$""").find(text) ?: return null
        val (numberPart, unit) = match.destructured
        val multiplier = when (unit) {
            "rb", "ribu", "k" -> 1_000L
            "jt", "juta" -> 1_000_000L
            "m", "miliar" -> 1_000_000_000L
            else -> 1L
        }
        return if (multiplier == 1L) {
            // Tanpa satuan: titik & koma dianggap pemisah ribuan (gaya Indonesia).
            numberPart.replace(".", "").replace(",", "").toLongOrNull()
        } else {
            // Dengan satuan: 1–2 digit setelah koma/titik = desimal ("1,5jt"); 3 digit = ribuan ("1.250 rb").
            val normalized = numberPart.replace(",", ".")
            val parts = normalized.split(".")
            val value = if (parts.size == 2 && parts[1].length <= 2) {
                normalized.toBigDecimalOrNull()
            } else {
                normalized.replace(".", "").toBigDecimalOrNull()
            } ?: return null
            value.multiply(multiplier.toBigDecimal()).toLong()
        }
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
