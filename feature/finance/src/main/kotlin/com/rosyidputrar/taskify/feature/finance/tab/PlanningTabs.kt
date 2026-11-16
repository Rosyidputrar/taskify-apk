package com.rosyidputrar.taskify.feature.finance.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.debtSummary
import com.rosyidputrar.taskify.core.domain.finance.monthlyNet
import com.rosyidputrar.taskify.core.domain.tasks.describeRule
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.finance.FinanceActions
import com.rosyidputrar.taskify.feature.finance.FinanceUiState
import com.rosyidputrar.taskify.feature.finance.component.BudgetArc
import com.rosyidputrar.taskify.feature.finance.component.CategoryBadge
import com.rosyidputrar.taskify.feature.finance.component.expenseColor
import com.rosyidputrar.taskify.feature.finance.component.incomeColor
import com.rosyidputrar.taskify.feature.finance.component.signedLabel

/** Dialog yang bisa dibuka dari tab. */
sealed interface FinanceDialog {
    data class Budget(val budget: BudgetStatus?) : FinanceDialog
    data class RecurringForm(val item: Recurring?) : FinanceDialog
    data object NewDebt : FinanceDialog
    data class Settle(val debt: Debt) : FinanceDialog
    data class WalletForm(val wallet: Wallet?) : FinanceDialog
    data class CategoryForm(val category: Category?, val kind: CategoryKind) : FinanceDialog
}

private fun LazyListScope.tabHeader(key: String, title: String, subtitle: String?, actionLabel: String?, onAction: () -> Unit) {
    item(key = "hd-$key") {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (actionLabel != null) {
                FilledTonalButton(onClick = onAction) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Text(actionLabel, Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ===================================================================== Anggaran

fun LazyListScope.budgetTab(state: FinanceUiState, onDialog: (FinanceDialog) -> Unit) {
    val budgets = state.monthData.budgets
    val warn = budgets.count { it.level != BudgetLevel.Ok }
    tabHeader(
        "budget",
        "Anggaran ${Dates.format(state.month, "MMMM")}",
        "Total pengeluaran ${Rupiah.format(state.monthData.totals.expense)}" + if (warn > 0) " · $warn perlu perhatian" else "",
        "Anggaran",
    ) { onDialog(FinanceDialog.Budget(null)) }
    if (state.loaded && budgets.isEmpty()) {
        item(key = "budget-empty") {
            EmptyState(TaskifyModule.Finance, "Belum ada anggaran", "Tetapkan batas belanja bulanan per kategori. Kamu akan diberi tanda saat mendekati batas.")
        }
    }
    items(budgets, key = { "b-${it.id}" }) { b -> BudgetRow(b) { onDialog(FinanceDialog.Budget(b)) } }
}

@Composable
private fun BudgetRow(b: BudgetStatus, onClick: () -> Unit) {
    val color = levelColor(b.level)
    val left = b.amount - b.spent
    val pct = (b.fraction * 100).toInt()
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ubah anggaran", onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { stateDescription = "$pct persen terpakai" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudgetArc(b.fraction, color, label = "$pct%")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (b.categoryId != null) {
                    CategoryBadge(b.icon, b.color, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${Rupiah.format(b.spent)} dari ${Rupiah.format(b.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (left >= 0) "Sisa ${Rupiah.format(left)}" else "Lewat ${Rupiah.format(-left)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (b.level == BudgetLevel.Ok) MaterialTheme.colorScheme.onSurfaceVariant else color,
            )
        }
    }
}

// ===================================================================== Berulang

fun LazyListScope.recurringTab(state: FinanceUiState, actions: FinanceActions, onDialog: (FinanceDialog) -> Unit) {
    val items = state.recurring
    val net = monthlyNet(items)
    val active = items.count { it.active }
    tabHeader(
        "rec",
        "Transaksi berulang",
        "$active aktif" + if (net != 0L) " · bulanan bersih ${if (net > 0) "+" else "−"}${Rupiah.format(kotlin.math.abs(net))}" else "",
        "Berulang",
    ) { onDialog(FinanceDialog.RecurringForm(null)) }
    if (state.due.isNotEmpty()) item(key = "rec-due") { DueCard(state.due, state.today, actions) }
    if (state.loaded && items.isEmpty()) {
        item(key = "rec-empty") {
            EmptyState(TaskifyModule.Finance, "Belum ada transaksi berulang", "Gaji, langganan, cicilan, tagihan listrik — catat sekali, Taskify mengirim notifikasi saat jatuh tempo (Catat / Lewati) atau mencatatnya otomatis — walau aplikasi tidak dibuka.")
        }
    }
    items(items, key = { "r-${it.id}" }) { r -> RecurringRow(r, state.today, actions) { onDialog(FinanceDialog.RecurringForm(r)) } }
}

@Composable
private fun RecurringRow(r: Recurring, today: String, actions: FinanceActions, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val next = r.nextRunOn
    val meta = listOfNotNull(
        describeRule(r.rrule, r.startOn),
        when {
            !r.active -> "dijeda"
            next == null -> "selesai"
            next <= today && !r.autoPost -> "jatuh tempo ${Dates.relativeDayLabel(next, today).lowercase()}"
            else -> "berikutnya ${Dates.relativeDayLabel(next, today).lowercase()}"
        },
        if (r.autoPost) "otomatis" else null,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ubah", onClick = onEdit).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(r.categoryIcon, r.categoryColor, transfer = r.kind == TxKind.Transfer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (r.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(signedLabel(r.kind, r.amount), style = MaterialTheme.typography.titleSmall, color = if (r.kind == TxKind.Income) incomeColor() else MaterialTheme.colorScheme.onSurface)
        }
        Switch(
            checked = r.active,
            onCheckedChange = { actions.setRecurringActive(r, it) },
            modifier = Modifier.semantics { contentDescription = if (r.active) "Jeda ${r.title}" else "Lanjutkan ${r.title}" },
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (r.active && next != null) {
                    DropdownMenuItem(text = { Text("Catat sekarang (${Dates.format(next, "d MMM")})") }, onClick = { menu = false; actions.postOccurrence(r) })
                    DropdownMenuItem(text = { Text("Lewati kejadian ini") }, onClick = { menu = false; actions.skipOccurrence(r) })
                }
                DropdownMenuItem(text = { Text("Hapus") }, onClick = { menu = false; actions.removeRecurring(r) })
            }
        }
    }
}

// ===================================================================== Hutang

fun LazyListScope.debtTab(state: FinanceUiState, actions: FinanceActions, onDialog: (FinanceDialog) -> Unit) {
    val summary = debtSummary(state.debts)
    item(key = "debt-sum") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Piutang", "orang berutang ke saya", summary.lent, incomeColor(), Modifier.weight(1f))
            SummaryTile("Hutang saya", "yang harus dibayar", summary.borrowed, expenseColor(), Modifier.weight(1f))
        }
    }
    tabHeader("debt", "Hutang & piutang", null, "Catat") { onDialog(FinanceDialog.NewDebt) }
    if (state.loaded && state.debts.isEmpty()) {
        item(key = "debt-empty") { EmptyState(TaskifyModule.Finance, "Tidak ada catatan", "Catat uang yang kamu pinjamkan atau pinjam agar tidak lupa.") }
    }
    items(state.debts, key = { "d-${it.id}" }) { d -> DebtRow(d, state.today, actions) { onDialog(FinanceDialog.Settle(d)) } }
}

@Composable
private fun SummaryTile(title: String, hint: String, amount: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Rupiah.format(amount), style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
        }
    }
}

@Composable
private fun DebtRow(d: Debt, today: String, actions: FinanceActions, onSettle: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val lent = d.direction == DebtDirection.Lent
    val color = if (lent) incomeColor() else expenseColor()
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (lent) Icons.AutoMirrored.Outlined.CallMade else Icons.AutoMirrored.Outlined.CallReceived, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (lent) "Ke " else "Dari ") + d.counterparty,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (d.settled) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                d.statusLabel(today) + (d.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = if (d.isOverdue(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(Rupiah.format(d.amount), style = MaterialTheme.typography.titleSmall, color = if (d.settled) MaterialTheme.colorScheme.onSurfaceVariant else color)
        if (!d.settled) {
            IconButton(onClick = onSettle) { Icon(Icons.Outlined.CheckCircle, contentDescription = "Tandai lunas") }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (d.settled) DropdownMenuItem(text = { Text("Batal lunas") }, onClick = { menu = false; actions.unsettleDebt(d) })
                DropdownMenuItem(text = { Text("Hapus beserta transaksinya") }, onClick = { menu = false; actions.removeDebt(d) })
            }
        }
    }
}

// ===================================================================== Kelola

fun LazyListScope.manageTab(state: FinanceUiState, onDialog: (FinanceDialog) -> Unit) {
    tabHeader("wallets", "Dompet", "${state.activeWallets.size} aktif · saldo ${Rupiah.format(state.totalBalance)}", "Dompet") { onDialog(FinanceDialog.WalletForm(null)) }
    items(state.wallets, key = { "w-${it.id}" }) { w ->
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Ubah dompet") { onDialog(FinanceDialog.WalletForm(w)) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(w.icon ?: w.type.icon, w.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.bodyLarge)
                Text(listOfNotNull(w.type.label, "${w.txCount} transaksi", if (w.archived) "diarsipkan" else null).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.archived) Icon(Icons.Outlined.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).size(18.dp))
            Text(Rupiah.format(w.balance), style = MaterialTheme.typography.titleSmall)
        }
    }
    for (kind in listOf(CategoryKind.Expense, CategoryKind.Income)) {
        val cats = state.categories.filter { it.kind == kind }
        tabHeader("cat-${kind.value}", "Kategori ${kind.label.lowercase()}", "${cats.size} kategori", "Kategori") { onDialog(FinanceDialog.CategoryForm(null, kind)) }
        items(cats, key = { "c-${it.id}" }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Ubah kategori") { onDialog(FinanceDialog.CategoryForm(c, c.kind)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(c.icon, c.color, size = 34.dp)
                Spacer(Modifier.width(14.dp))
                Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${c.txCount} transaksi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item(key = "manage-hint") {
        Text(
            "Dompet yang sudah punya transaksi tidak bisa dihapus — arsipkan saja agar riwayat & saldo tetap utuh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

