package com.rosyidputrar.taskify.feature.finance

import androidx.lifecycle.SavedStateHandle
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.feature.finance.component.moneyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_DRAFT = "tx_draft"
private const val KEY_BASELINE = "tx_baseline"

/** Transaksi yang ada ([id]) atau baru di [date] / dompet [walletId]. */
data class TxEditorArgs(val id: String? = null, val date: String? = null, val walletId: String? = null)

data class TxDraft(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val id: String? = null,
    val kind: TxKind = TxKind.Expense,
    val amountText: String = "",
    val walletId: String = "",
    val toWalletId: String? = null,
    val categoryId: String? = null,
    val date: String = "",
    val time: String? = null,
    val title: String = "",
    val note: String = "",
    val error: String? = null,
    /** Transaksi milik hutang/seri berulang: tetap bisa diubah, tapi diberi keterangan. */
    val linked: String? = null,
    /** Tautan ke tugas asal (dipertahankan saat transaksi diedit agar task_id tidak terhapus). */
    val taskId: String? = null,
) {
    val amount: Long? get() = Rupiah.parse(amountText)

    /** Bagian yang bisa diubah pengguna — pembanding "ada perubahan?". */
    internal fun editable() = listOf(kind, amountText, walletId, toWalletId, categoryId, date, time, title, note)

    /** Disimpan ke SavedStateHandle: draf bertahan saat sistem mematikan proses. */
    internal fun toSaved(): ArrayList<String?> = arrayListOf(
        id, kind.name, amountText, walletId, toWalletId, categoryId, date, time, title, note, linked, taskId,
    )

    internal companion object {
        fun fromSaved(v: List<String?>): TxDraft = TxDraft(
            loaded = true, id = v[0], kind = TxKind.entries.firstOrNull { it.name == v[1] } ?: TxKind.Expense, amountText = v[2].orEmpty(),
            walletId = v[3].orEmpty(), toWalletId = v[4], categoryId = v[5], date = v[6].orEmpty(), time = v[7], title = v[8].orEmpty(),
            note = v[9].orEmpty(), linked = v[10], taskId = v.getOrNull(11),
        )
    }
}

data class TxEditorState(val draft: TxDraft = TxDraft(), val wallets: List<Wallet> = emptyList(), val categories: List<Category> = emptyList())

class TxEditorViewModel(
    private val args: TxEditorArgs,
    private val repo: FinanceRepository,
    private val messenger: Messenger,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = MutableStateFlow(TxDraft())

    /** Isian saat editor dibuka; dibandingkan dengan isian sekarang untuk "Simpan perubahan?". */
    private var baseline: List<Any?>? = null

    /** Ada ketikan yang akan hilang bila editor ditinggalkan. */
    val dirty: Boolean get() {
        val d = state.value.draft
        val base = baseline?.toMutableList() ?: return false
        // Dompet default diisi otomatis setelah daftar dompet tiba — bukan perubahan pengguna.
        if (base[2] == "") base[2] = state.value.wallets.firstOrNull()?.id ?: ""
        return d.loaded && !d.missing && d.editable() != base
    }

    val state: StateFlow<TxEditorState> = combine(draft, repo.watchWallets(includeArchived = false), repo.watchCategories()) { d, w, c ->
        // Dompet default = dompet aktif pertama, begitu daftar dompet tersedia.
        val fixed = if (d.loaded && d.walletId.isEmpty() && w.isNotEmpty()) d.copy(walletId = w.first().id) else d
        TxEditorState(fixed, w, c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxEditorState())

    init {
        val restored = saved.get<ArrayList<String?>>(KEY_DRAFT)?.let { runCatching { TxDraft.fromSaved(it) }.getOrNull() }
        if (restored != null) {
            draft.value = restored
            baseline = saved.get<ArrayList<String?>>(KEY_BASELINE)?.let { runCatching { TxDraft.fromSaved(it).editable() }.getOrNull() }
        } else launchSafe {
            if (args.id != null) {
                val t = repo.getTransaction(args.id)
                draft.value = if (t == null) TxDraft(loaded = true, missing = true) else TxDraft(
                    loaded = true, id = t.id, kind = t.kind, amountText = moneyText(t.amount), walletId = t.walletId, toWalletId = t.toWalletId,
                    categoryId = t.categoryId, date = t.occurredOn, time = t.occurredTime?.take(5), title = t.title.orEmpty(), note = t.note.orEmpty(),
                    linked = when {
                        t.debtId != null -> "Transaksi ini bagian dari catatan hutang/piutang."
                        t.recurringId != null -> "Dicatat dari transaksi berulang."
                        else -> null
                    },
                    taskId = t.taskId,
                )
            } else {
                draft.value = TxDraft(loaded = true, date = args.date ?: Dates.today(), walletId = args.walletId.orEmpty())
            }
            markBaseline()
        }
    }

    /** Patokan "belum diubah" = isian setelah dimuat (dompet default ikut diisi bila belum ada). */
    private fun markBaseline() {
        val d = draft.value
        baseline = d.editable()
        saved[KEY_BASELINE] = d.toSaved()
    }

    fun update(block: (TxDraft) -> TxDraft) {
        draft.update { block(it).copy(error = null) }
        saved[KEY_DRAFT] = draft.value.toSaved()
    }

    fun setKind(kind: TxKind) = update { it.copy(kind = kind, categoryId = if (kind == it.kind) it.categoryId else null) }

    /** Tambah cepat (+10rb dst.) ke nominal yang sudah diketik. */
    fun addAmount(delta: Long) = update { it.copy(amountText = moneyText((it.amount ?: 0) + delta)) }

    fun save(onDone: () -> Unit) {
        val d = state.value.draft
        val amount = d.amount
        if (amount == null || amount <= 0) {
            draft.update { it.copy(error = "Isi nominal yang valid, mis. 25000 atau 25rb") }
            return
        }
        if (d.walletId.isEmpty()) {
            draft.update { it.copy(error = "Buat dompet dulu di tab Kelola") }
            return
        }
        launchSafe {
            try {
                val input = TxInput(d.kind, amount, d.walletId, d.toWalletId, d.categoryId, d.date, d.time, d.title, d.note, taskId = d.taskId)
                if (d.id == null) {
                    val id = repo.createTx(input)
                    messenger.undoable("${d.kind.label} ${Rupiah.format(amount)} dicatat") { repo.removeTx(id) }
                } else {
                    repo.updateTx(d.id, input)
                    messenger.show("Transaksi diperbarui")
                }
                onDone()
            } catch (e: IllegalArgumentException) {
                draft.update { it.copy(error = e.message) }
            }
        }
    }

    fun remove(onDone: () -> Unit) {
        val id = state.value.draft.id ?: return onDone()
        launchSafe {
            repo.removeTx(id)
            messenger.undoable("Transaksi dihapus") { repo.restoreTx(id) }
            onDone()
        }
    }
}
