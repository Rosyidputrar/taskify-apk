package com.rosyidputrar.taskify.feature.finance

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.SkeletonCard
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import com.rosyidputrar.taskify.core.designsystem.component.fadingEdges
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.finance.Csv
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.finance.component.BudgetSheet
import com.rosyidputrar.taskify.feature.finance.component.CategorySheet
import com.rosyidputrar.taskify.feature.finance.component.DebtSheet
import com.rosyidputrar.taskify.feature.finance.component.RecurringSheet
import com.rosyidputrar.taskify.feature.finance.component.SettleDialog
import com.rosyidputrar.taskify.feature.finance.component.WalletCard
import com.rosyidputrar.taskify.feature.finance.component.WalletSheet
import com.rosyidputrar.taskify.feature.finance.component.incomeColor
import com.rosyidputrar.taskify.feature.finance.tab.FinanceDialog
import com.rosyidputrar.taskify.feature.finance.tab.budgetTab
import com.rosyidputrar.taskify.feature.finance.tab.debtTab
import com.rosyidputrar.taskify.feature.finance.tab.manageTab
import com.rosyidputrar.taskify.feature.finance.tab.overviewTab
import com.rosyidputrar.taskify.feature.finance.tab.recurringTab
import com.rosyidputrar.taskify.feature.finance.tab.transactionsTab
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FinanceScreen(
    contentPadding: PaddingValues,
    onOpenTx: (String) -> Unit,
    onNewTx: (walletId: String?) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    /** Konten tambahan di bawah kepala layar (mis. kartu draf transaksi dari Notifikasi). */
    topSlot: @Composable () -> Unit = {},
    viewModel: FinanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) writeCsv(context, uri, csv)
    }
    FinanceContent(
        state, viewModel, contentPadding, onOpenTx, onNewTx, onBack, backButton,
        topSlot = topSlot,
        onExport = {
            // exportCsv suspend (query ekspor tanpa LIMIT) → jalankan di scope layar.
            scope.launch {
                viewModel.exportCsv()?.let { (name, csv) ->
                    pendingCsv = csv
                    saveCsv.launch(name)
                }
            }
        },
    )
}

/** BOM UTF-8 agar Excel menampilkan huruf non-ASCII dengan benar. */
private fun writeCsv(context: Context, uri: Uri, csv: String) {
    context.contentResolver.openOutputStream(uri)?.use { it.write((Csv.BOM + csv).toByteArray(Charsets.UTF_8)) }
}

@Composable
fun FinanceContent(
    state: FinanceUiState,
    actions: FinanceActions,
    contentPadding: PaddingValues,
    onOpenTx: (String) -> Unit,
    onNewTx: (walletId: String?) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    onExport: () -> Unit = {},
    topSlot: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticFinanceActions(actions, haptics) }
    var dialog by remember { mutableStateOf<FinanceDialog?>(null) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Finance)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = contentPadding) {
        item(key = "header") {
            val t = state.monthData.totals
            ModuleHeader(
                TaskifyModule.Finance,
                "Keuangan",
                subtitle = "${Dates.format(state.month, "MMMM")}: keluar ${Rupiah.compact(t.expense)} · masuk ${Rupiah.compact(t.income)}",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    FilledTonalIconButton(
                        onClick = { onNewTx(state.walletId) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Finance)),
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Catat transaksi") }
                },
            )
        }
        item(key = "top-slot") { topSlot() }
        item(key = "hero") {
            if (state.loaded) BalanceHero(state, actions)
            else SkeletonCard(TaskifyModule.Finance, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), lines = 3, leadingDot = false)
        }
        stickyHeader(key = "tabs") {
            // 6 tab tidak muat di HP: tab di tepi dulu terpotong di tengah kata ("…kasan").
            // Tepi kini memudar hanya ke arah yang masih bisa digeser.
            val tabScroll = rememberScrollState()
            PrimaryScrollableTabRow(
                selectedTabIndex = state.tab.ordinal,
                scrollState = tabScroll,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).fadingEdges(tabScroll, width = 40.dp),
            ) {
                FinanceTab.entries.forEach { tab ->
                    Tab(selected = state.tab == tab, onClick = { actions.setTab(tab) }, text = { Text(tab.label) })
                }
            }
        }
        if (state.tab.monthly) item(key = "month") { MonthSwitcher(state, actions) }
        if (!state.loaded) item(key = "fin-skeleton") { ListSkeleton(TaskifyModule.Finance, rows = 4, lines = 2) }
        else when (state.tab) {
            FinanceTab.Overview -> overviewTab(state, actions, onOpenTx)
            FinanceTab.Transactions -> transactionsTab(state, actions, onOpenTx, onExport)
            FinanceTab.Budget -> budgetTab(state) { dialog = it }
            FinanceTab.Recurring -> recurringTab(state, actions) { dialog = it }
            FinanceTab.Debt -> debtTab(state, actions) { dialog = it }
            FinanceTab.Manage -> manageTab(state) { dialog = it }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        is FinanceDialog.Budget -> BudgetSheet(
            d.budget,
            state.categories,
            state.monthData.budgets.map { it.categoryId ?: "" }.toSet(),
            onDismiss = close,
            onSave = { cat, amount, alert -> actions.saveBudget(d.budget?.id, cat, amount, alert, close) },
            onDelete = d.budget?.let { b -> { actions.removeBudget(b); close() } },
        )
        is FinanceDialog.RecurringForm -> RecurringSheet(
            d.item, state.activeWallets, state.categories, state.today, close,
            onSave = { actions.saveRecurring(it, close) },
            onDelete = d.item?.let { r -> { actions.removeRecurring(r); close() } },
        )
        FinanceDialog.NewDebt -> DebtSheet(state.activeWallets, state.today, close) { actions.createDebt(it, close) }
        is FinanceDialog.Settle -> SettleDialog(d.debt, state.activeWallets, close) { walletId -> actions.settleDebt(d.debt, walletId); close() }
        is FinanceDialog.WalletForm -> WalletSheet(
            d.wallet, close,
            onSave = { actions.saveWallet(d.wallet?.id, it, close) },
            onArchive = d.wallet?.let { w -> { actions.setWalletArchived(w, !w.archived); close() } },
            onDelete = d.wallet?.takeIf { it.txCount == 0 }?.let { w -> { actions.removeWallet(w, close) } },
        )
        is FinanceDialog.CategoryForm -> CategorySheet(
            d.category, d.kind, close,
            onSave = { actions.saveCategory(d.category?.id, it, close) },
            onDelete = d.category?.let { c -> { actions.removeCategory(c); close() } },
        )
    }
}

/** Kartu saldo total + karusel dompet (ketuk dompet = filter transaksi). */
@Composable
private fun BalanceHero(state: FinanceUiState, actions: FinanceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        IndexCard(TaskifyModule.Finance, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = 14.dp) {
            Text("SALDO SEMUA DOMPET", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Finance))
            Text(
                Rupiah.format(state.totalBalance),
                style = TaskifyTextStyles.numeric,
                color = if (state.totalBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp).semantics { contentDescription = "Saldo semua dompet ${Rupiah.format(state.totalBalance)}" },
            )
            val net = state.monthData.totals.net
            Text(
                "${state.activeWallets.size} dompet · bulan ini " + (if (net >= 0) "+" else "−") + Rupiah.format(kotlin.math.abs(net)),
                style = MaterialTheme.typography.labelLarge,
                color = if (net >= 0) incomeColor() else MaterialTheme.colorScheme.error,
            )
        }
        if (state.activeWallets.isNotEmpty()) {
            val walletScroll = rememberLazyListState()
            LazyRow(state = walletScroll, modifier = Modifier.fadingEdges(walletScroll), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.activeWallets, key = { it.id }) { w ->
                    WalletCard(w, selected = state.walletId == w.id, onClick = { actions.setWallet(if (state.walletId == w.id) null else w.id) })
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(state: FinanceUiState, actions: FinanceActions) {
    val current = state.month == Dates.monthStart(state.today)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
        Text(Dates.format(state.month, "MMMM yyyy"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (!current) TextButton(onClick = { actions.shiftMonth(java.time.Period.between(Dates.parse(state.month), Dates.parse(Dates.monthStart(state.today))).toTotalMonths()) }) { Text("Bulan ini") }
        IconButton(onClick = { actions.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Bulan berikutnya") }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticFinanceActions(private val a: FinanceActions, private val h: TaskifyHaptics) : FinanceActions by a {
    override fun setTab(tab: FinanceTab) { h.tick(); a.setTab(tab) }
    override fun shiftMonth(amount: Long) { h.tick(); a.shiftMonth(amount) }
    override fun setWallet(id: String?) { h.tick(); a.setWallet(id) }
    override fun setSliceKind(kind: CategoryKind) { h.tick(); a.setSliceKind(kind) }
    override fun setTxFilter(filter: TxListFilter) { h.tick(); a.setTxFilter(filter) }
    override fun postOccurrence(r: Recurring) { h.success(); a.postOccurrence(r) }
    override fun settleDebt(d: Debt, walletId: String?) { h.success(); a.settleDebt(d, walletId) }
    override fun setRecurringActive(r: Recurring, active: Boolean) { h.toggle(active); a.setRecurringActive(r, active) }
    override fun setWalletArchived(w: Wallet, archived: Boolean) { h.toggle(archived); a.setWalletArchived(w, archived) }
}
