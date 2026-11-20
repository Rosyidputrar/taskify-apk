package com.rosyidputrar.taskify.core.domain.finance

import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.domain.time.Dates

enum class TxKind(val value: String, val label: String) {
    Expense("expense", "Pengeluaran"), Income("income", "Pemasukan"), Transfer("transfer", "Transfer");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: Expense
    }
}

enum class CategoryKind(val value: String, val label: String) {
    Expense("expense", "Pengeluaran"), Income("income", "Pemasukan");

    companion object {
        fun from(value: String?) = if (value == "income") Income else Expense
    }
}

/** Jenis dompet; [icon] = nama ikon default (disimpan di DB, sama dengan webapp). */
enum class WalletType(val value: String, val label: String, val icon: String) {
    Cash("cash", "Tunai", "banknote"),
    Bank("bank", "Bank", "landmark"),
    EWallet("ewallet", "E-wallet", "smartphone"),
    Investment("investment", "Investasi", "trending-up"),
    Other("other", "Lainnya", "wallet");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: Other
    }
}

/** Nama ikon kategori/dompet yang disimpan di DB (kebab-case, daftar sama dengan webapp). */
val FINANCE_ICON_NAMES = listOf(
    "utensils", "coffee", "shopping-cart", "car", "fuel", "plane", "receipt", "zap", "droplets", "wifi",
    "smartphone", "house", "shopping-bag", "shirt", "gamepad-2", "film", "heart-pulse", "dumbbell",
    "graduation-cap", "book-open", "baby", "paw-print", "wrench", "repeat", "gift", "wallet", "laptop",
    "briefcase", "trending-up", "piggy-bank", "hand-coins", "banknote", "landmark", "circle-ellipsis",
)

data class Wallet(
    val id: String,
    val name: String,
    val type: WalletType,
    val initialBalance: Long,
    val color: String?,
    val icon: String?,
    val position: Double,
    val archived: Boolean,
    val balance: Long = initialBalance,
    val txCount: Int = 0,
)

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val color: String?,
    val icon: String?,
    val position: Double,
    val txCount: Int = 0,
)

/** Baris transaksi + nama kategori/dompet hasil JOIN. */
data class Transaction(
    val id: String,
    val kind: TxKind,
    val amount: Long,
    val walletId: String,
    val toWalletId: String?,
    val categoryId: String?,
    val occurredOn: String,
    val occurredTime: String?,
    val title: String?,
    val note: String?,
    val taskId: String? = null,
    val recurringId: String? = null,
    val debtId: String? = null,
    val createdAt: String = "",
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val walletName: String? = null,
    val toWalletName: String? = null,
    /** Aplikasi asal bila dicatat dari notifikasi (hanya di HP ini) — ikon aplikasinya dipakai di daftar. */
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
) {
    /** Judul tampilan: keterangan → kategori → jenis (port `txTitle`). */
    val displayTitle: String
        get() = title ?: when (kind) {
            TxKind.Transfer -> "Transfer"
            TxKind.Income -> categoryName ?: "Pemasukan"
            TxKind.Expense -> categoryName ?: "Pengeluaran"
        }

    /** Nominal bertanda dari sudut pandang statistik (transfer = 0 arah). */
    val signedAmount: Long get() = when (kind) { TxKind.Income -> amount; TxKind.Expense -> -amount; TxKind.Transfer -> 0 }
}

data class PeriodTotals(val income: Long = 0, val expense: Long = 0, val count: Int = 0) {
    val net: Long get() = income - expense
}

data class CategorySlice(val categoryId: String?, val name: String, val color: String?, val icon: String?, val total: Long, val count: Int)

data class DayTotals(val day: String, val income: Long, val expense: Long)

data class BudgetStatus(
    val id: String,
    val categoryId: String?,
    val amount: Long,
    val alertPct: Int,
    val startMonth: String,
    val endMonth: String?,
    val name: String,
    val color: String?,
    val icon: String?,
    val spent: Long,
) {
    val level: BudgetLevel get() = budgetLevel(spent, amount, alertPct)
    val fraction: Float get() = if (amount <= 0) 0f else spent.toFloat() / amount
}

enum class BudgetLevel { Ok, Warn, Over }

/** ok / warn (≥ alert_pct) / over (≥ 100%) — port `budgetLevel`. */
fun budgetLevel(spent: Long, amount: Long, alertPct: Int): BudgetLevel {
    val pct = spent.toDouble() / amount * 100
    return when {
        pct >= 100 -> BudgetLevel.Over
        pct >= alertPct -> BudgetLevel.Warn
        else -> BudgetLevel.Ok
    }
}

/** Ambang peringatan anggaran yang ditawarkan. */
val ALERT_OPTIONS = listOf(50, 70, 80, 90, 100)

data class Recurring(
    val id: String,
    val kind: TxKind,
    val amount: Long,
    val walletId: String,
    val toWalletId: String?,
    val categoryId: String?,
    val title: String,
    val note: String?,
    val rrule: String,
    val startOn: String,
    val endOn: String?,
    val nextRunOn: String?,
    val autoPost: Boolean,
    val active: Boolean,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val walletName: String? = null,
) {
    val schedule: RecurringSchedule get() = RecurringSchedule(rrule, startOn, endOn, nextRunOn)

    /** Tagihan manual (auto_post = 0) yang menunggu Catat / Lewati. */
    fun isDue(today: String) = active && !autoPost && nextRunOn != null && nextRunOn <= today
}

/** Selisih bulanan bersih seri aktif bulanan (transfer diabaikan) — port tab Berulang. */
fun monthlyNet(items: List<Recurring>): Long = items
    .filter { it.active && it.kind != TxKind.Transfer && Regex("FREQ=MONTHLY;INTERVAL=1(;|$)").containsMatchIn(it.rrule) }
    .sumOf { if (it.kind == TxKind.Expense) -it.amount else it.amount }

enum class DebtDirection(val value: String, val label: String) {
    Lent("lent", "Saya meminjamkan"), Borrowed("borrowed", "Saya meminjam");

    companion object {
        fun from(value: String?) = if (value == "borrowed") Borrowed else Lent
    }
}

data class Debt(
    val id: String,
    val direction: DebtDirection,
    val counterparty: String,
    val amount: Long,
    val dueDate: String?,
    val note: String?,
    val settledAt: String?,
    val createdAt: String,
) {
    val settled: Boolean get() = settledAt != null
    fun isOverdue(today: String) = !settled && dueDate != null && dueDate < today

    fun statusLabel(today: String): String = when {
        settled -> "Lunas"
        dueDate != null -> "Jatuh tempo ${Dates.relativeDayLabel(dueDate, today).lowercase()}"
        else -> "Tanpa jatuh tempo"
    }
}

data class DebtSummary(val lent: Long, val borrowed: Long)

fun debtSummary(debts: List<Debt>) = DebtSummary(
    lent = debts.filter { !it.settled && it.direction == DebtDirection.Lent }.sumOf { it.amount },
    borrowed = debts.filter { !it.settled && it.direction == DebtDirection.Borrowed }.sumOf { it.amount },
)

class FinanceException(message: String) : IllegalArgumentException(message)

/** Masukan form transaksi. */
data class TxInput(
    val kind: TxKind,
    val amount: Long,
    val walletId: String,
    val toWalletId: String? = null,
    val categoryId: String? = null,
    val occurredOn: String,
    val occurredTime: String? = null,
    val title: String? = null,
    val note: String? = null,
    val taskId: String? = null,
)

/** Nilai kolom siap tulis — sudah lolos CHECK constraint Postgres. */
data class TxValues(
    val kind: TxKind,
    val amount: Long,
    val walletId: String,
    val toWalletId: String?,
    val categoryId: String?,
    val occurredOn: String,
    val occurredTime: String?,
    val title: String?,
    val note: String?,
    val taskId: String?,
)

/**
 * Normalisasi sesuai CHECK constraint (port `normalizeTx`): nominal > 0, transfer wajib dompet
 * tujuan yang berbeda dan tanpa kategori, judul dipotong ke batas, teks kosong → null.
 */
fun normalizeTx(input: TxInput): TxValues {
    if (input.amount <= 0) throw FinanceException("Nominal harus lebih dari 0")
    val transfer = input.kind == TxKind.Transfer
    if (transfer && (input.toWalletId == null || input.toWalletId == input.walletId)) {
        throw FinanceException("Pilih dompet tujuan yang berbeda")
    }
    return TxValues(
        kind = input.kind,
        amount = input.amount,
        walletId = input.walletId,
        toWalletId = if (transfer) input.toWalletId else null,
        categoryId = if (transfer) null else input.categoryId,
        occurredOn = input.occurredOn,
        occurredTime = Dates.toDbTime(input.occurredTime),
        title = input.title?.trim()?.takeIf { it.isNotEmpty() }?.let { Limits.clip(it, Limits.TRANSACTION_TITLE) },
        note = input.note?.trim()?.takeIf { it.isNotEmpty() },
        taskId = input.taskId,
    )
}

/** Kelompokkan transaksi (sudah terurut tanggal turun) per tanggal + total keluar/masuk hari itu. */
data class TxDay(val date: String, val items: List<Transaction>, val income: Long, val expense: Long)

fun groupByDay(rows: List<Transaction>): List<TxDay> = rows.groupBy { it.occurredOn }.map { (date, items) ->
    val stat = items.filter { it.debtId == null }
    TxDay(date, items, stat.filter { it.kind == TxKind.Income }.sumOf { it.amount }, stat.filter { it.kind == TxKind.Expense }.sumOf { it.amount })
}
