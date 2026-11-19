package com.rosyidputrar.taskify.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.device.AccessProbe
import com.rosyidputrar.taskify.core.device.DeviceModulePrefs
import com.rosyidputrar.taskify.core.device.NotifSettings
import com.rosyidputrar.taskify.core.domain.capture.CATEGORY_KEYWORDS
import com.rosyidputrar.taskify.core.domain.capture.matchCategory
import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.NotifStorageLevel
import com.rosyidputrar.taskify.core.domain.device.TaskHint
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppCount
import com.rosyidputrar.taskify.core.store.device.DraftState
import com.rosyidputrar.taskify.core.store.device.NotifAction
import com.rosyidputrar.taskify.core.store.device.NotifEntry
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.Parcel
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

enum class NotifView(val label: String) { Timeline("Lini waktu"), PerApp("Per aplikasi") }

/** Saring daftar: kategori, atau "Perlu ditindak" (chip Tugas?/transaksi/paket yang belum ditangani). */
enum class NotifFilter(val label: String, val category: NotifCategory? = null) {
    All("Semua"),
    Actionable("Perlu ditindak"),
    Chat("Chat", NotifCategory.Chat),
    Money("Bank & dompet", NotifCategory.Money),
    Shopping("Belanja & kurir", NotifCategory.Shopping),
    Email("Email", NotifCategory.Email),
    Other("Lainnya", NotifCategory.Other),
    ;

    fun matches(e: NotifEntry): Boolean = when (this) {
        All -> true
        Actionable -> e.handled == null && (e.isTaskLike || e.draft == DraftState.Pending)
        else -> e.category == category
    }
}

val NotifEntry.isTaskLike: Boolean get() = TaskHint.looksLikeTask(category, text)

data class NotifAccess(
    val granted: Boolean = false,
    val connected: Boolean = true,
    val disconnectedAt: Long = 0,
    val restrictedHint: Boolean = false,
    val oem: String? = null,
)

/** Hasil suntingan satu draf transaksi sebelum disimpan. */
data class DraftEdit(val amount: Long, val kind: TxKind, val title: String, val walletId: String, val categoryId: String?)

data class NotificationsUiState(
    val today: String = Dates.today(),
    val day: String = today,
    val access: NotifAccess = NotifAccess(),
    val settings: NotifSettings = NotifSettings(),
    val entries: List<NotifEntry> = emptyList(),
    val total: Int = 0,
    val previousTotal: Int? = null,
    val appCounts: List<AppCount> = emptyList(),
    val filter: NotifFilter = NotifFilter.All,
    val app: String? = null,
    val view: NotifView = NotifView.Timeline,
    val query: String = "",
    val drafts: List<NotifEntry> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val parcels: List<Parcel> = emptyList(),
    /** Aplikasi yang pernah mengirim notifikasi dalam masa simpan (untuk daftar pengecualian). */
    val knownApps: List<AppCount> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Pita hari: sebanyak masa simpan, berakhir hari ini. */
    val days: List<String> get() = (settings.retentionDays - 1 downTo 0).map { Dates.addDays(today, -it.toLong()) }

    val visible: List<NotifEntry>
        get() {
            val q = query.trim().lowercase()
            return entries.filter { e ->
                filter.matches(e) && (app == null || e.pkg == app) &&
                    (q.isEmpty() || listOfNotNull(e.title, e.text, e.appLabel, e.conversation).any { it.lowercase().contains(q) })
            }
        }

    /** Jumlah per jam (semua notifikasi hari itu, tanpa saringan). */
    val hourly: IntArray
        get() {
            val out = IntArray(24)
            entries.forEach { out[hourOf(it.postedAt)]++ }
            return out
        }

    val appLabel: String? get() = app?.let { pkg -> appCounts.firstOrNull { it.pkg == pkg }?.label ?: entries.firstOrNull { it.pkg == pkg }?.appLabel }

    /** Draf yang dompetnya sudah diketahui → bisa "Simpan semua". */
    val readyDrafts: List<NotifEntry> get() = drafts.filter { d -> walletFor(d) != null }

    fun walletFor(d: NotifEntry): String? = settings.wallets[d.pkg]?.takeIf { id -> wallets.any { it.id == id } }

    /** Tebakan kategori dari nama merchant/pengirim. */
    fun guessCategory(d: NotifEntry): String? {
        val money = d.money ?: return null
        val title = money.counterparty ?: d.title.orEmpty()
        val hint = CATEGORY_KEYWORDS.firstOrNull { it.first.containsMatchIn(title) }?.second
        return matchCategory(categories, { it.kind.value }, { it.name }, money.kind.value, hint, title)?.id
    }
}

fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atOffset(Dates.WIB).hour

interface NotificationsActions {
    fun refreshAccess() {}
    fun reconnect() {}
    fun selectDay(day: String) {}
    fun setFilter(filter: NotifFilter) {}
    fun setApp(pkg: String?) {}
    fun setView(view: NotifView) {}
    fun setQuery(query: String) {}
    fun hide(entry: NotifEntry) {}
    fun makeTask(entry: NotifEntry) {}
    fun makeNote(entry: NotifEntry) {}
    fun saveDraft(entry: NotifEntry, edit: DraftEdit) {}
    fun dismissDraft(entry: NotifEntry) {}
    fun saveAllDrafts() {}
    fun setLevel(level: NotifStorageLevel) {}
    fun setRetention(days: Int) {}
    fun setExcluded(pkg: String, excluded: Boolean) {}
    fun setIgnoreOngoing(on: Boolean) {}
    fun setDraftAlerts(on: Boolean) {}
    fun forgetWallet(pkg: String) {}
    fun clearAll() {}

    companion object {
        val None = object : NotificationsActions {}
    }
}

private data class Selection(val day: String, val filter: NotifFilter, val app: String?, val view: NotifView, val query: String)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val repo: NotificationLogRepository,
    private val prefs: DeviceModulePrefs,
    private val probe: AccessProbe,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val finance: FinanceRepository,
    private val messenger: Messenger,
    initialApp: String? = null,
    /** Minta sistem menyambungkan ulang pencatat (DeviceAccess.rebindListener). */
    private val rebind: () -> Unit = {},
) : ViewModel(), NotificationsActions {
    private val today = Dates.today()
    private val selection = MutableStateFlow(Selection(today, NotifFilter.All, initialApp, NotifView.Timeline, ""))
    private val access = MutableStateFlow(readAccess(prefs.listenerConnected.value))

    private val dayData = selection.flatMapLatest { s ->
        combine(
            repo.watchDay(s.day),
            repo.watchDailyTotals(Dates.addDays(s.day, -1), s.day),
            repo.watchAppCounts(s.day, s.day),
        ) { entries, totals, apps -> Triple(entries, totals, apps) }
    }

    private val side = combine(
        repo.watchDrafts(),
        finance.watchWallets(includeArchived = false),
        finance.watchCategories(),
        repo.watchParcels(System.currentTimeMillis() - PARCEL_WINDOW_MS),
        repo.watchAppCounts(Dates.addDays(today, -30), today),
    ) { drafts, wallets, categories, parcels, known -> Side(drafts, wallets, categories, parcels, known) }

    private data class Side(val drafts: List<NotifEntry>, val wallets: List<Wallet>, val categories: List<Category>, val parcels: List<Parcel>, val known: List<AppCount>)

    val state: StateFlow<NotificationsUiState> = combine(
        selection,
        combine(access, prefs.listenerConnected) { a, c -> a.copy(connected = c, disconnectedAt = prefs.listenerDisconnectedAt) },
        prefs.notif,
        dayData,
        side,
    ) { s, acc, settings, (entries, totals, apps), side ->
        NotificationsUiState(
            today = today,
            day = s.day,
            access = acc,
            settings = settings,
            entries = entries,
            total = totals[s.day] ?: 0,
            previousTotal = totals[Dates.addDays(s.day, -1)],
            appCounts = apps,
            filter = s.filter,
            app = s.app,
            view = s.view,
            query = s.query,
            drafts = side.drafts,
            wallets = side.wallets,
            categories = side.categories,
            parcels = side.parcels,
            knownApps = side.known,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState(today = today, app = initialApp))

    private fun readAccess(connected: Boolean) = NotifAccess(
        granted = probe.notifications(),
        connected = connected,
        disconnectedAt = prefs.listenerDisconnectedAt,
        restrictedHint = probe.restrictedSettingsHint(),
        oem = probe.oem(),
    )

    private fun select(block: (Selection) -> Selection) {
        selection.value = block(selection.value)
    }

    override fun refreshAccess() {
        access.value = readAccess(prefs.listenerConnected.value)
    }

    override fun reconnect() {
        rebind()
        messenger.show("Menyambungkan ulang pencatat notifikasi…")
        refreshAccess()
    }

    override fun selectDay(day: String) = select { it.copy(day = day) }
    override fun setFilter(filter: NotifFilter) = select { it.copy(filter = filter) }
    override fun setApp(pkg: String?) = select { it.copy(app = pkg) }
    override fun setView(view: NotifView) = select { it.copy(view = view) }
    override fun setQuery(query: String) = select { it.copy(query = query) }

    override fun hide(entry: NotifEntry) {
        launchSafe {
            repo.setHidden(entry.id, true)
            messenger.undoable("Notifikasi disembunyikan") { repo.setHidden(entry.id, false) }
        }
    }

    override fun makeTask(entry: NotifEntry) {
        launchSafe {
            val title = TaskHint.taskTitle(entry.category, entry.conversation ?: entry.title, entry.text)
            val parsed = entry.text?.let { parseQuickAdd(it, Dates.today()) }
            val id = tasks.create(
                NewTask(
                    title = title,
                    dueDate = parsed?.dueDate,
                    dueTime = parsed?.dueTime,
                    notes = origin(entry),
                ),
            )
            repo.markHandled(entry.id, NotifAction.Task)
            messenger.undoable("Tugas dibuat" + (parsed?.dueDate?.let { " · " + Dates.relativeDayLabel(it, Dates.today()) } ?: "")) {
                tasks.get(id)?.let { tasks.remove(it) }
                repo.markHandled(entry.id, null)
            }
        }
    }

    override fun makeNote(entry: NotifEntry) {
        launchSafe {
            val id = notes.create(
                date = Dates.today(),
                title = listOfNotNull(entry.conversation, entry.title).firstOrNull() ?: entry.appLabel,
                content = listOfNotNull(entry.text, origin(entry)).joinToString("\n\n"),
            )
            repo.markHandled(entry.id, NotifAction.Note)
            messenger.undoable("Catatan dibuat") {
                notes.remove(id)
                repo.markHandled(entry.id, null)
            }
        }
    }

    override fun saveDraft(entry: NotifEntry, edit: DraftEdit) {
        launchSafe {
            val txId = save(entry, edit)
            messenger.undoable("Transaksi dicatat") {
                finance.removeTx(txId)
                repo.setDraft(entry.id, DraftState.Pending)
                repo.markHandled(entry.id, null)
            }
        }
    }

    private suspend fun save(entry: NotifEntry, edit: DraftEdit): String {
        val time = formatClockTime(entry.postedAt).replace('.', ':') + ":00"
        val id = finance.createTx(
            TxInput(
                kind = edit.kind,
                amount = edit.amount,
                walletId = edit.walletId,
                categoryId = edit.categoryId,
                occurredOn = entry.day,
                occurredTime = time,
                title = edit.title.ifBlank { null },
                note = "Dari notifikasi ${entry.money?.source ?: entry.appLabel}",
            ),
            // Ikon aplikasi asal dipakai di daftar Keuangan (disimpan di HP ini saja).
            source = entry.pkg to entry.appLabel,
        )
        repo.setDraft(entry.id, DraftState.Saved)
        repo.markHandled(entry.id, NotifAction.Transaction)
        if (prefs.notif.value.wallets[entry.pkg] != edit.walletId) prefs.updateNotif { it.copy(wallets = it.wallets + (entry.pkg to edit.walletId)) }
        return id
    }

    override fun dismissDraft(entry: NotifEntry) {
        launchSafe {
            repo.setDraft(entry.id, DraftState.Dismissed)
            messenger.undoable("Draf diabaikan") { repo.setDraft(entry.id, DraftState.Pending) }
        }
    }

    override fun saveAllDrafts() {
        val s = state.value
        val ready = s.readyDrafts
        if (ready.isEmpty()) return
        launchSafe {
            ready.forEach { d ->
                val m = d.money ?: return@forEach
                save(d, DraftEdit(m.amount, m.kind, defaultTitle(d), s.walletFor(d)!!, s.guessCategory(d)))
            }
            messenger.show("${ready.size} transaksi dicatat")
        }
    }

    override fun setLevel(level: NotifStorageLevel) = prefs.updateNotif { it.copy(level = level) }
    override fun setRetention(days: Int) {
        prefs.updateNotif { it.copy(retentionDays = days) }
        val today = Dates.today()
        launchSafe { repo.prune(Dates.addDays(today, -(days - 1).toLong()), Dates.addDays(today, -NotificationLogRepository.COUNT_KEEP_DAYS.toLong())) }
        if (selection.value.day < Dates.addDays(today, -(days - 1).toLong())) select { it.copy(day = today) }
    }

    override fun setExcluded(pkg: String, excluded: Boolean) {
        prefs.updateNotif { it.copy(excluded = if (excluded) it.excluded + pkg else it.excluded - pkg) }
        if (excluded) launchSafe { repo.forgetApp(pkg) }
    }

    override fun setIgnoreOngoing(on: Boolean) = prefs.updateNotif { it.copy(ignoreOngoing = on) }
    override fun setDraftAlerts(on: Boolean) = prefs.updateNotif { it.copy(draftAlerts = on) }
    override fun forgetWallet(pkg: String) = prefs.updateNotif { it.copy(wallets = it.wallets - pkg) }

    override fun clearAll() {
        launchSafe {
            repo.clearAll()
            messenger.show("Riwayat notifikasi dihapus")
        }
    }

    private fun origin(e: NotifEntry) = "Dari notifikasi ${e.appLabel} · ${formatClockTime(e.postedAt)}"

    companion object {
        const val PARCEL_WINDOW_MS = 7 * 86_400_000L

        fun defaultTitle(d: NotifEntry): String = d.money?.counterparty ?: d.money?.source ?: d.appLabel
    }
}
