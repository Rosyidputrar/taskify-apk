package com.rosyidputrar.taskify.core.domain.finance

import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * CSV ramah Excel versi Indonesia (port `lib/csv.ts`): pemisah ';', BOM ditambahkan saat menulis
 * file. Teks berawalan = + - @ (CSV injection) diberi kutip tunggal agar dibaca sebagai teks.
 */
object Csv {
    const val BOM = "﻿"
    private val FORMULA_START = Regex("^[=+\\-@\\t\\r]")
    private val PLAIN_NUMBER = Regex("^[+-]?\\d+(?:[.,]\\d+)?$")
    private val NEEDS_QUOTE = Regex("[\"\\n\\r;,]")

    fun build(header: List<String>, rows: List<List<Any?>>, separator: String = ";"): String =
        (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(separator) { escape(it) } }

    private fun escape(value: Any?): String {
        if (value == null) return ""
        var s = value.toString()
        if (value is String && FORMULA_START.containsMatchIn(s) && !PLAIN_NUMBER.matches(s)) s = "'$s"
        return if (NEEDS_QUOTE.containsMatchIn(s) || s != s.trim()) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}

/** Ekspor transaksi: kolom sama dengan webapp; pengeluaran bertanda negatif. */
fun transactionsCsv(rows: List<Transaction>): String = Csv.build(
    listOf("Tanggal", "Jam", "Jenis", "Keterangan", "Kategori", "Dompet", "Dompet tujuan", "Nominal", "Catatan"),
    rows.map { r ->
        listOf(
            r.occurredOn, Dates.shortTime(r.occurredTime), r.kind.label, r.displayTitle, r.categoryName,
            r.walletName, r.toWalletName, if (r.kind == TxKind.Expense) -r.amount else r.amount, r.note,
        )
    },
)

fun transactionsCsvName(month: String) = "taskify-transaksi-${month.take(7)}.csv"
