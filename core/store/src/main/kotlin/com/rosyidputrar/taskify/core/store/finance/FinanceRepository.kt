package com.rosyidputrar.taskify.core.store.finance

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.powersync.db.internal.ConnectionContext
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.DayTotals
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.FinanceException
import com.rosyidputrar.taskify.core.domain.finance.PeriodTotals
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.RecurringSchedule
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.TxValues
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.domain.finance.debtOpenTxId
import com.rosyidputrar.taskify.core.domain.finance.debtSettleTxId
import com.rosyidputrar.taskify.core.domain.finance.dueOccurrences
import com.rosyidputrar.taskify.core.domain.finance.firstOccurrenceFrom
import com.rosyidputrar.taskify.core.domain.finance.normalizeTx
import com.rosyidputrar.taskify.core.domain.finance.occurrenceAfter
import com.rosyidputrar.taskify.core.domain.finance.recurringTxId
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TxFilter(
    val from: String,
    val to: String,
    val kind: TxKind? = null,
    val walletId: String? = null,
    val categoryId: String? = null,
    val search: String = "",
    val limit: Int = 2000,
)

data class WalletInput(val name: String, val type: WalletType, val initialBalance: Long, val color: String, val icon: String)
data class CategoryInput(val name: String, val kind: CategoryKind, val color: String, val icon: String)

data class RecurringInput(
    val id: String? = null,
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
    val autoPost: Boolean,
)

data class DebtInput(
    val direction: DebtDirection,
    val counterparty: String,
    val amount: Long,
    val dueDate: String?,
    val note: String?,
    /** Dompet untuk mencatat uang keluar/masuk saat hutang dibuat (opsional). */
    val walletId: String?,
)

private const val TX_SELECT = """
  SELECT t.id, t.kind, t.amount, t.wallet_id, t.to_wallet_id, t.category_id, t.occurred_on, t.occurred_time,
         t.title, t.note, t.task_id, t.recurring_id, t.debt_id, t.created_at,
         c.name, c.color, c.icon, w.name, tw.name, s.package, s.app_label
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.category_id
    LEFT JOIN wallets w ON w.id = t.wallet_id
    LEFT JOIN wallets tw ON tw.id = t.to_wallet_id
    LEFT JOIN tx_sources s ON s.id = t.id"""

private fun SqlCursor.toTx() = Transaction(
    id = getString(0)!!, kind = TxKind.from(getString(1)), amount = getLong(2) ?: 0, walletId = getString(3) ?: "",
    toWalletId = getString(4), categoryId = getString(5), occurredOn = getString(6) ?: "", occurredTime = getString(7),
    title = getString(8), note = getString(9), taskId = getString(10), recurringId = getString(11), debtId = getString(12),
    createdAt = getString(13) ?: "", categoryName = getString(14), categoryColor = getString(15), categoryIcon = getString(16),
    walletName = getString(17), toWalletName = getString(18), sourcePackage = getString(19)?.takeIf { it.isNotBlank() }, sourceLabel = getString(20),
)

private const val RECURRING_SELECT = """
  SELECT r.id, r.kind, r.amount, r.wallet_id, r.to_wallet_id, r.category_id, r.title, r.note, r.rrule, r.start_on,
         r.end_on, r.next_run_on, r.auto_post, r.is_active, c.name, c.color, c.icon, w.name
    FROM recurring_transactions r
    LEFT JOIN categories c ON c.id = r.category_id
    LEFT JOIN wallets w ON w.id = r.wallet_id"""

private fun SqlCursor.toRecurring() = Recurring(
    id = getString(0)!!, kind = TxKind.from(getString(1)), amount = getLong(2) ?: 0, walletId = getString(3) ?: "",
    toWalletId = getString(4), categoryId = getString(5), title = getString(6) ?: "", note = getString(7),
    rrule = getString(8) ?: "", startOn = getString(9) ?: "", endOn = getString(10), nextRunOn = getString(11),
    autoPost = int(12) == 1, active = int(13) == 1, categoryName = getString(14), categoryColor = getString(15),
    categoryIcon = getString(16), walletName = getString(17),
)

/**
 * Semua baca & tulis modul Keuangan — port `finance-queries.ts` + `finance-actions.ts`.
 *
 * Aturan hitung (sama dengan webapp):
 *  - Saldo dompet = saldo awal + pemasukan − pengeluaran − transfer keluar + transfer masuk.
 *  - Statistik (ringkasan, kategori, anggaran) MENGABAIKAN transfer & transaksi hutang
 *    (debt_id terisi) karena itu perpindahan uang, bukan pemasukan/pengeluaran.
 *  - Transaksi hasil seri berulang & hutang ber-ID deterministik → tidak dobel antar perangkat.
 */
class FinanceRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    // ================================================================== baca

    fun watchWallets(includeArchived: Boolean = true): Flow<List<Wallet>> = db.watch(
        """SELECT w.id, w.name, w.type, w.initial_balance, w.color, w.icon, w.position, w.is_archived,
                  w.initial_balance + COALESCE((
                    SELECT SUM(CASE
                      WHEN t.kind = 'income'   AND t.wallet_id = w.id    THEN t.amount
                      WHEN t.kind = 'expense'  AND t.wallet_id = w.id    THEN -t.amount
                      WHEN t.kind = 'transfer' AND t.wallet_id = w.id    THEN -t.amount
                      WHEN t.kind = 'transfer' AND t.to_wallet_id = w.id THEN t.amount
                      ELSE 0 END)
                    FROM transactions t
                   WHERE t.deleted_at IS NULL AND (t.wallet_id = w.id OR t.to_wallet_id = w.id)), 0),
                  (SELECT COUNT(*) FROM transactions t
                    WHERE t.deleted_at IS NULL AND (t.wallet_id = w.id OR t.to_wallet_id = w.id))
             FROM wallets w
            WHERE w.deleted_at IS NULL AND (?1 = 1 OR w.is_archived = 0)
            ORDER BY w.is_archived, w.position, w.created_at""",
        listOf(if (includeArchived) 1 else 0),
    ) { c ->
        Wallet(
            c.getString(0)!!, c.getString(1) ?: "", WalletType.from(c.getString(2)), c.getLong(3) ?: 0, c.getString(4), c.getString(5),
            c.getDouble(6) ?: 0.0, c.int(7) == 1, c.getLong(8) ?: 0, c.int(9),
        )
    }

    fun watchCategories(kind: CategoryKind? = null): Flow<List<Category>> = db.watch(
        """SELECT c.id, c.name, c.kind, c.color, c.icon, c.position,
                  (SELECT COUNT(*) FROM transactions t WHERE t.deleted_at IS NULL AND t.category_id = c.id)
             FROM categories c
            WHERE c.deleted_at IS NULL AND c.is_archived = 0 AND (?1 IS NULL OR c.kind = ?1)
            ORDER BY c.kind DESC, c.position, c.name""",
        listOf(kind?.value),
    ) { c -> Category(c.getString(0)!!, c.getString(1) ?: "", CategoryKind.from(c.getString(2)), c.getString(3), c.getString(4), c.getDouble(5) ?: 0.0, c.int(6)) }

    fun watchTransactions(f: TxFilter): Flow<List<Transaction>> = db.watch(
        """$TX_SELECT
           WHERE t.deleted_at IS NULL
             AND t.occurred_on BETWEEN ?1 AND ?2
             AND (?3 IS NULL OR t.kind = ?3)
             AND (?4 IS NULL OR t.wallet_id = ?4 OR t.to_wallet_id = ?4)
             AND (?5 IS NULL OR t.category_id = ?5)
             AND (?6 = '' OR LOWER(COALESCE(t.title, '') || ' ' || COALESCE(t.note, '') || ' ' ||
                                   COALESCE(c.name, '')) LIKE '%' || ?6 || '%' ESCAPE '\')
           ORDER BY t.occurred_on DESC, COALESCE(t.occurred_time, '99') DESC, t.created_at DESC
           LIMIT ?7""",
        listOf(f.from, f.to, f.kind?.value, f.walletId, f.categoryId, escapeLike(f.search.trim().lowercase()), f.limit),
    ) { it.toTx() }

    fun watchTransaction(id: String): Flow<Transaction?> = db.watch("$TX_SELECT WHERE t.id = ?", listOf(id)) { it.toTx() }.map { it.firstOrNull() }

    suspend fun getTransaction(id: String): Transaction? = db.getOptional("$TX_SELECT WHERE t.id = ?", listOf(id)) { it.toTx() }

    /** Total pemasukan & pengeluaran (tanpa transfer & hutang) dalam rentang. */
    fun watchPeriodTotals(from: String, to: String): Flow<PeriodTotals> = db.watch(
        """SELECT COALESCE(SUM(CASE WHEN kind = 'income' THEN amount END), 0),
                  COALESCE(SUM(CASE WHEN kind = 'expense' THEN amount END), 0),
                  COUNT(*)
             FROM transactions
            WHERE deleted_at IS NULL AND debt_id IS NULL AND kind <> 'transfer'
              AND occurred_on BETWEEN ?1 AND ?2""",
        listOf(from, to),
    ) { c -> PeriodTotals(c.getLong(0) ?: 0, c.getLong(1) ?: 0, c.int(2)) }.map { it.firstOrNull() ?: PeriodTotals() }

    fun watchCategoryBreakdown(from: String, to: String, kind: CategoryKind): Flow<List<CategorySlice>> = db.watch(
        """SELECT t.category_id, COALESCE(c.name, 'Tanpa kategori'), c.color, c.icon, SUM(t.amount), COUNT(*)
             FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.deleted_at IS NULL AND t.debt_id IS NULL AND t.kind = ?3
              AND t.occurred_on BETWEEN ?1 AND ?2
            GROUP BY t.category_id
            ORDER BY 5 DESC""",
        listOf(from, to, kind.value),
    ) { c -> CategorySlice(c.getString(0), c.getString(1) ?: "", c.getString(2), c.getString(3), c.getLong(4) ?: 0, c.int(5)) }

    fun watchDailyTotals(from: String, to: String): Flow<Map<String, DayTotals>> = db.watch(
        """SELECT occurred_on,
                  COALESCE(SUM(CASE WHEN kind = 'income' THEN amount END), 0),
                  COALESCE(SUM(CASE WHEN kind = 'expense' THEN amount END), 0)
             FROM transactions
            WHERE deleted_at IS NULL AND debt_id IS NULL AND kind <> 'transfer'
              AND occurred_on BETWEEN ?1 AND ?2
            GROUP BY occurred_on""",
        listOf(from, to),
    ) { c -> DayTotals(c.getString(0)!!, c.getLong(1) ?: 0, c.getLong(2) ?: 0) }.map { rows -> rows.associateBy { it.day } }

    /** Anggaran yang berlaku di bulan [month] (YYYY-MM-01) beserta realisasinya. */
    fun watchBudgets(month: String, monthLast: String): Flow<List<BudgetStatus>> = db.watch(
        """SELECT b.id, b.category_id, b.amount, b.alert_pct, b.start_month, b.end_month,
                  COALESCE(c.name, 'Total pengeluaran'), c.color, c.icon,
                  COALESCE((SELECT SUM(t.amount) FROM transactions t
                             WHERE t.deleted_at IS NULL AND t.debt_id IS NULL AND t.kind = 'expense'
                               AND t.occurred_on BETWEEN ?1 AND ?2
                               AND (b.category_id IS NULL OR t.category_id = b.category_id)), 0)
             FROM budgets b LEFT JOIN categories c ON c.id = b.category_id
            WHERE b.deleted_at IS NULL AND b.start_month <= ?1
              AND (b.end_month IS NULL OR b.end_month >= ?1)
            ORDER BY b.category_id IS NOT NULL, c.position, c.name""",
        listOf(month, monthLast),
    ) { c ->
        BudgetStatus(
            c.getString(0)!!, c.getString(1), c.getLong(2) ?: 0, c.int(3), c.getString(4) ?: "", c.getString(5),
            c.getString(6) ?: "", c.getString(7), c.getString(8), c.getLong(9) ?: 0,
        )
    }

    fun watchRecurring(): Flow<List<Recurring>> = db.watch(
        "$RECURRING_SELECT WHERE r.deleted_at IS NULL ORDER BY r.is_active DESC, r.next_run_on IS NULL, r.next_run_on, r.title",
    ) { it.toRecurring() }

    fun watchDebts(): Flow<List<Debt>> = db.watch(
        """SELECT id, direction, counterparty, amount, due_date, note, settled_at, created_at
             FROM debts WHERE deleted_at IS NULL
            ORDER BY settled_at IS NOT NULL, due_date IS NULL, due_date, created_at DESC""",
    ) { c -> Debt(c.getString(0)!!, DebtDirection.from(c.getString(1)), c.getString(2) ?: "", c.getLong(3) ?: 0, c.getString(4), c.getString(5), c.getString(6), c.getString(7) ?: "") }

    // ================================================================== transaksi

    /** [source] = aplikasi asal (paket, nama) bila dicatat dari notifikasi; disimpan di HP ini saja. */
    suspend fun createTx(input: TxInput, source: Pair<String, String>? = null): String {
        val id = Ids.newId()
        val values = normalizeTx(input)
        val userId = users.require()
        db.writeTransactionAsync { tx ->
            tx.insertTx(id, userId, values)
            if (source != null) tx.executeAsync("INSERT INTO tx_sources (id, package, app_label) VALUES (?, ?, ?)", listOf(id, source.first, source.second))
        }
        return id
    }

    /**
     * Transaksi dari notifikasi yang dicatat sebelum asal aplikasinya disimpan: cocokkan dengan log
     * notifikasi (draf disetujui, nominal & tanggal sama, catatan "Dari notifikasi X" sama). Hanya
     * yang cocok dengan TEPAT SATU aplikasi yang diisi — tidak menebak. Mengembalikan jumlah terisi.
     */
    suspend fun backfillNotificationSources(): Int {
        // Asal milik transaksi yang sudah dihapus permanen tidak berguna lagi.
        db.execute("DELETE FROM tx_sources WHERE id NOT IN (SELECT id FROM transactions)")
        val rows = db.getAll(
            """SELECT t.id, MIN(n.package), MIN(n.app_label)
                 FROM transactions t
                 JOIN notif_log n ON n.draft = 'saved' AND n.money_amount = t.amount AND n.day = t.occurred_on
                      AND t.note = 'Dari notifikasi ' || COALESCE(n.money_source, n.app_label)
                WHERE t.deleted_at IS NULL AND t.note LIKE 'Dari notifikasi %'
                  AND NOT EXISTS (SELECT 1 FROM tx_sources s WHERE s.id = t.id)
                GROUP BY t.id
               HAVING COUNT(DISTINCT n.package) = 1""",
            emptyList(),
        ) { Triple(it.getString(0)!!, it.getString(1) ?: "", it.getString(2) ?: "") }
        if (rows.isEmpty()) return 0
        db.writeTransactionAsync { tx ->
            for ((id, pkg, label) in rows) if (pkg.isNotBlank()) tx.executeAsync("INSERT INTO tx_sources (id, package, app_label) VALUES (?, ?, ?)", listOf(id, pkg, label))
        }
        return rows.count { it.second.isNotBlank() }
    }

    suspend fun updateTx(id: String, input: TxInput) {
        val v = normalizeTx(input)
        db.execute(
            """UPDATE transactions SET kind = ?, amount = ?, wallet_id = ?, to_wallet_id = ?, category_id = ?, occurred_on = ?,
                      occurred_time = ?, title = ?, note = ?, task_id = ?, updated_at = ? WHERE id = ?""",
            listOf(v.kind.value, v.amount, v.walletId, v.toWalletId, v.categoryId, v.occurredOn, v.occurredTime, v.title, v.note, v.taskId, Timestamps.nowIso(), id),
        )
    }

    suspend fun removeTx(id: String) = softDelete("transactions", id)
    suspend fun restoreTx(id: String) = restore("transactions", id)

    // ================================================================== dompet

    suspend fun createWallet(v: WalletInput): String {
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            """INSERT INTO wallets (id, user_id, created_at, updated_at, name, type, currency, initial_balance, color, icon, position, is_archived)
               VALUES (?, ?, ?, ?, ?, ?, 'IDR', ?, ?, ?, ?, 0)""",
            listOf(id, users.require(), now, now, v.name.trim().take(60), v.type.value, v.initialBalance, v.color, v.icon, System.currentTimeMillis().toDouble()),
        )
        return id
    }

    suspend fun updateWallet(id: String, v: WalletInput) {
        db.execute(
            "UPDATE wallets SET name = ?, type = ?, initial_balance = ?, color = ?, icon = ?, updated_at = ? WHERE id = ?",
            listOf(v.name.trim().take(60), v.type.value, v.initialBalance, v.color, v.icon, Timestamps.nowIso(), id),
        )
    }

    suspend fun setWalletArchived(id: String, archived: Boolean) {
        db.execute("UPDATE wallets SET is_archived = ?, updated_at = ? WHERE id = ?", listOf(if (archived) 1 else 0, Timestamps.nowIso(), id))
    }

    /** Hanya dompet tanpa transaksi yang boleh dihapus; selebihnya diarsipkan (dicek di sini juga). */
    suspend fun removeWallet(id: String) {
        val used = db.get("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL AND (wallet_id = ?1 OR to_wallet_id = ?1)", listOf(id)) { it.int(0) }
        if (used > 0) throw FinanceException("Dompet ini punya transaksi. Arsipkan saja agar riwayat tetap utuh.")
        softDelete("wallets", id)
    }

    // ================================================================== kategori

    suspend fun createCategory(v: CategoryInput): String {
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            """INSERT INTO categories (id, user_id, created_at, updated_at, parent_id, name, kind, color, icon, position, is_archived)
               VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 0)""",
            listOf(id, users.require(), now, now, v.name.trim().take(60), v.kind.value, v.color, v.icon, System.currentTimeMillis().toDouble()),
        )
        return id
    }

    suspend fun updateCategory(id: String, name: String, color: String, icon: String) {
        db.execute("UPDATE categories SET name = ?, color = ?, icon = ?, updated_at = ? WHERE id = ?", listOf(name.trim().take(60), color, icon, Timestamps.nowIso(), id))
    }

    /** Transaksi lama tetap menyimpan category_id → nama tetap tampil di riwayat. */
    suspend fun removeCategory(id: String) = softDelete("categories", id)

    // ================================================================== anggaran

    suspend fun saveBudget(id: String?, categoryId: String?, amount: Long, alertPct: Int, startMonth: String) {
        if (amount <= 0) throw FinanceException("Anggaran harus lebih dari 0")
        val now = Timestamps.nowIso()
        if (id != null) {
            db.execute("UPDATE budgets SET amount = ?, alert_pct = ?, updated_at = ? WHERE id = ?", listOf(amount, alertPct, now, id))
        } else {
            db.execute(
                """INSERT INTO budgets (id, user_id, created_at, updated_at, category_id, amount, start_month, end_month, alert_pct)
                   VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)""",
                listOf(Ids.newId(), users.require(), now, now, categoryId, amount, startMonth, alertPct),
            )
        }
    }

    suspend fun removeBudget(id: String) = softDelete("budgets", id)

    /** Pulihkan anggaran; false bila sudah ada anggaran lain untuk kategori yang sama (tidak membuat duplikat). */
    suspend fun restoreBudget(id: String): Boolean {
        val categoryId = db.getOptional("SELECT category_id FROM budgets WHERE id = ?", listOf(id)) { it.getString(0) ?: "" } ?: return false
        val clash = db.get(
            "SELECT COUNT(*) FROM budgets WHERE deleted_at IS NULL AND COALESCE(category_id, '') = ?",
            listOf(categoryId),
        ) { it.int(0) }
        if (clash > 0) return false
        restore("budgets", id)
        return true
    }

    // ================================================================== berulang

    suspend fun saveRecurring(v: RecurringInput, today: String = Dates.today()) {
        val base = normalizeTx(TxInput(v.kind, v.amount, v.walletId, v.toWalletId, v.categoryId, v.startOn))
        val schedule = RecurringSchedule(v.rrule, v.startOn, v.endOn)
        val title = Limits.clip(v.title.trim(), Limits.RECURRING_TITLE)
        if (title.isEmpty()) throw FinanceException("Isi nama transaksi berulang")
        val note = v.note?.trim()?.takeIf { it.isNotEmpty() }
        val now = Timestamps.nowIso()
        if (v.id != null) {
            // Jadwal diubah → kejadian berikutnya dihitung ulang mulai hari ini (atau start_on jika di masa depan).
            val from = if (v.startOn > today) v.startOn else today
            db.execute(
                """UPDATE recurring_transactions SET kind = ?, amount = ?, wallet_id = ?, to_wallet_id = ?, category_id = ?, title = ?, note = ?,
                          rrule = ?, start_on = ?, end_on = ?, auto_post = ?, next_run_on = ?, updated_at = ? WHERE id = ?""",
                listOf(base.kind.value, base.amount, base.walletId, base.toWalletId, base.categoryId, title, note, v.rrule, v.startOn, v.endOn,
                    if (v.autoPost) 1 else 0, firstOccurrenceFrom(schedule, from), now, v.id),
            )
        } else {
            db.execute(
                """INSERT INTO recurring_transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, to_wallet_id, category_id,
                          title, note, rrule, start_on, end_on, next_run_on, auto_post, is_active)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)""",
                listOf(Ids.newId(), users.require(), now, now, base.kind.value, base.amount, base.walletId, base.toWalletId, base.categoryId,
                    title, note, v.rrule, v.startOn, v.endOn, firstOccurrenceFrom(schedule, v.startOn), if (v.autoPost) 1 else 0),
            )
        }
    }

    suspend fun setRecurringActive(r: Recurring, active: Boolean, today: String = Dates.today()) {
        val from = if (today > r.startOn) today else r.startOn
        if (active) {
            db.execute(
                "UPDATE recurring_transactions SET is_active = 1, next_run_on = ?, updated_at = ? WHERE id = ?",
                listOf(firstOccurrenceFrom(r.schedule, from), Timestamps.nowIso(), r.id),
            )
        } else {
            db.execute("UPDATE recurring_transactions SET is_active = 0, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), r.id))
        }
    }

    suspend fun removeRecurring(id: String) = softDelete("recurring_transactions", id)

    /** Catat kejadian `next_run_on` sebagai transaksi, lalu maju ke kejadian berikutnya. */
    suspend fun postOccurrence(r: Recurring): Boolean {
        val date = r.nextRunOn ?: return false
        val userId = users.require()
        val values = normalizeTx(TxInput(r.kind, r.amount, r.walletId, r.toWalletId, r.categoryId, date, title = r.title, note = r.note))
        db.writeTransactionAsync { tx ->
            tx.insertIfMissing(recurringTxId(r.id, date), userId, values, recurringId = r.id)
            tx.executeAsync(
                "UPDATE recurring_transactions SET next_run_on = ?, updated_at = ? WHERE id = ?",
                listOf(occurrenceAfter(r.schedule, date), Timestamps.nowIso(), r.id),
            )
        }
        return true
    }

    suspend fun skipOccurrence(r: Recurring) {
        val date = r.nextRunOn ?: return
        db.execute("UPDATE recurring_transactions SET next_run_on = ?, updated_at = ? WHERE id = ?", listOf(occurrenceAfter(r.schedule, date), Timestamps.nowIso(), r.id))
    }

    /**
     * Catat otomatis seri auto_post yang jatuh tempo (port `useRecurringProcessor`). ID
     * deterministik mencegah dobel bila perangkat lain sudah mencatat kejadian yang sama.
     * Mengembalikan jumlah transaksi baru.
     */
    suspend fun processDueRecurring(today: String = Dates.today()): Int {
        val due = db.getAll(
            "$RECURRING_SELECT WHERE r.deleted_at IS NULL AND r.is_active = 1 AND r.auto_post = 1 AND r.next_run_on <= ?",
            listOf(today),
        ) { it.toRecurring() }
        if (due.isEmpty()) return 0
        val userId = users.require()
        var posted = 0
        for (r in due) {
            val (dates, next) = dueOccurrences(r.schedule, today)
            db.writeTransactionAsync { tx ->
                for (date in dates) {
                    val values = normalizeTx(TxInput(r.kind, r.amount, r.walletId, r.toWalletId, r.categoryId, date, title = r.title, note = r.note))
                    if (tx.insertIfMissing(recurringTxId(r.id, date), userId, values, recurringId = r.id)) posted++
                }
                tx.executeAsync("UPDATE recurring_transactions SET next_run_on = ?, updated_at = ? WHERE id = ?", listOf(next, Timestamps.nowIso(), r.id))
            }
        }
        return posted
    }

    // ================================================================== hutang / piutang

    suspend fun createDebt(v: DebtInput, today: String = Dates.today()): String {
        if (v.amount <= 0) throw FinanceException("Nominal harus lebih dari 0")
        val counterparty = Limits.clip(v.counterparty.trim(), Limits.COUNTERPARTY)
        if (counterparty.isEmpty()) throw FinanceException("Isi nama orangnya")
        val id = Ids.newId()
        val userId = users.require()
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync(
                """INSERT INTO debts (id, user_id, created_at, updated_at, direction, counterparty, amount, due_date, note, settled_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)""",
                listOf(id, userId, now, now, v.direction.value, counterparty, v.amount, v.dueDate, v.note?.trim()?.takeIf { it.isNotEmpty() }),
            )
            if (v.walletId != null) {
                // Meminjamkan = uang keluar; meminjam = uang masuk.
                val values = normalizeTx(
                    TxInput(
                        kind = if (v.direction == DebtDirection.Lent) TxKind.Expense else TxKind.Income,
                        amount = v.amount, walletId = v.walletId, occurredOn = today,
                        title = if (v.direction == DebtDirection.Lent) "Pinjamkan ke $counterparty" else "Pinjam dari $counterparty",
                    ),
                )
                tx.insertIfMissing(debtOpenTxId(id), userId, values, debtId = id)
            }
        }
        return id
    }

    suspend fun settleDebt(d: Debt, walletId: String?, today: String = Dates.today()) {
        val userId = users.require()
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE debts SET settled_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, d.id))
            if (walletId != null) {
                val txId = debtSettleTxId(d.id)
                val v = normalizeTx(
                    TxInput(
                        kind = if (d.direction == DebtDirection.Lent) TxKind.Income else TxKind.Expense,
                        amount = d.amount, walletId = walletId, occurredOn = today,
                        title = if (d.direction == DebtDirection.Lent) "Pelunasan dari ${d.counterparty}" else "Pelunasan ke ${d.counterparty}",
                    ),
                )
                // Bisa jadi baris lama (soft-deleted saat "batal lunas") → hidupkan lagi.
                val exists = tx.getAllAsync("SELECT id FROM transactions WHERE id = ?", listOf(txId)) { it.getString(0)!! }.isNotEmpty()
                if (exists) {
                    tx.executeAsync(
                        """UPDATE transactions SET kind = ?, amount = ?, wallet_id = ?, to_wallet_id = NULL, category_id = NULL, occurred_on = ?,
                                  occurred_time = NULL, title = ?, note = NULL, debt_id = ?, deleted_at = NULL, updated_at = ? WHERE id = ?""",
                        listOf(v.kind.value, v.amount, v.walletId, v.occurredOn, v.title, d.id, now, txId),
                    )
                } else {
                    tx.insertTx(txId, userId, v, debtId = d.id)
                }
            }
        }
    }

    suspend fun unsettleDebt(d: Debt) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE debts SET settled_at = NULL, updated_at = ? WHERE id = ?", listOf(now, d.id))
            tx.executeAsync(
                "UPDATE transactions SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
                listOf(now, now, debtSettleTxId(d.id)),
            )
        }
    }

    /** Hapus catatan hutang beserta transaksi dompet yang terkait. */
    suspend fun removeDebt(d: Debt) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE debts SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, d.id))
            tx.executeAsync("UPDATE transactions SET deleted_at = ?, updated_at = ? WHERE debt_id = ? AND deleted_at IS NULL", listOf(now, now, d.id))
        }
    }

    // ================================================================== bantuan

    private suspend fun softDelete(table: String, id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE $table SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    private suspend fun restore(table: String, id: String) {
        db.execute("UPDATE $table SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }

    private suspend fun ConnectionContext.insertTx(id: String, userId: String, v: TxValues, recurringId: String? = null, debtId: String? = null) {
        val now = Timestamps.nowIso()
        executeAsync(
            """INSERT INTO transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, to_wallet_id, category_id,
                      occurred_on, occurred_time, title, note, task_id, recurring_id, debt_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            listOf(id, userId, now, now, v.kind.value, v.amount, v.walletId, v.toWalletId, v.categoryId, v.occurredOn, v.occurredTime,
                v.title, v.note, v.taskId, recurringId, debtId),
        )
    }

    /** Insert transaksi ber-ID tetap bila belum ada (termasuk yang ada di Sampah). */
    private suspend fun ConnectionContext.insertIfMissing(id: String, userId: String, v: TxValues, recurringId: String? = null, debtId: String? = null): Boolean {
        val exists = getAllAsync("SELECT id FROM transactions WHERE id = ?", listOf(id)) { it.getString(0)!! }.isNotEmpty()
        if (exists) return false
        insertTx(id, userId, v, recurringId, debtId)
        return true
    }

    /** Pengeluaran terbaru (tujuan "struk untuk transaksi" saat berbagi foto). */
    fun watchRecentExpenses(limit: Int = 15): Flow<List<RecentExpense>> = db.watch(
        """SELECT t.id, COALESCE(NULLIF(t.title, ''), c.name, 'Pengeluaran'), t.amount, t.occurred_on FROM transactions t
             LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.deleted_at IS NULL AND t.kind = 'expense' ORDER BY t.occurred_on DESC, t.created_at DESC LIMIT ?""",
        listOf(limit.toLong()),
    ) { c -> RecentExpense(c.getString(0)!!, c.getString(1) ?: "Pengeluaran", c.getLong(2) ?: 0, c.getString(3) ?: "") }
}

data class RecentExpense(val id: String, val title: String, val amount: Long, val day: String)
