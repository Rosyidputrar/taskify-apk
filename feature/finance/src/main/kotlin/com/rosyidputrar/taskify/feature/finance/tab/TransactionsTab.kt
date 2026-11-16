package com.rosyidputrar.taskify.feature.finance.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.finance.FinanceActions
import com.rosyidputrar.taskify.feature.finance.FinanceUiState
import com.rosyidputrar.taskify.feature.finance.component.TxRow

private val KIND_CHIPS = listOf(null to "Semua", TxKind.Expense to "Keluar", TxKind.Income to "Masuk", TxKind.Transfer to "Transfer")

fun LazyListScope.transactionsTab(state: FinanceUiState, actions: FinanceActions, onOpenTx: (String) -> Unit, onExport: () -> Unit) {
    val f = state.txFilter
    item(key = "tx-search") {
        OutlinedTextField(
            value = f.search,
            onValueChange = { actions.setTxFilter(f.copy(search = it)) },
            placeholder = { Text("Cari keterangan, catatan, kategori…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (f.search.isNotEmpty()) {
                { IconButton(onClick = { actions.setTxFilter(f.copy(search = "")) }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
    item(key = "tx-filters") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(KIND_CHIPS, key = { it.second }) { (kind, label) ->
                FilterChip(selected = f.kind == kind, onClick = { actions.setTxFilter(f.copy(kind = kind, categoryId = if (kind == TxKind.Transfer) null else f.categoryId)) }, label = { Text(label) })
            }
            item { WalletFilterChip(state, actions) }
            item { CategoryFilterChip(state, actions) }
            item {
                AssistChip(onClick = onExport, label = { Text("CSV") }, leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) })
            }
        }
    }
    val days = state.days
    if (state.loaded && days.isEmpty()) {
        item(key = "tx-empty") {
            val filtered = f.active || state.walletId != null
            EmptyState(
                TaskifyModule.Finance,
                if (filtered) "Tidak ada yang cocok" else "Belum ada transaksi",
                if (filtered) "Coba lepas filter atau ganti bulan." else "Transaksi ${Dates.format(state.month, "MMMM yyyy")} akan tersusun per hari di sini.",
            )
        }
    }
    days.forEach { day ->
        stickyHeader(key = "tx-h-${day.date}") {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Dates.relativeDayLabel(day.date, state.today).uppercase(), style = TaskifyTextStyles.indexLabel, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.weight(1f))
                val parts = buildList {
                    if (day.income > 0) add("+" + Rupiah.compact(day.income))
                    if (day.expense > 0) add("−" + Rupiah.compact(day.expense))
                }
                Text(parts.joinToString("  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(day.items, key = { "tx-${it.id}" }) { tx ->
            TxRow(tx, onOpen = { onOpenTx(tx.id) }, onDelete = { actions.removeTx(tx) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun WalletFilterChip(state: FinanceUiState, actions: FinanceActions) {
    var open by remember { mutableStateOf(false) }
    val current = state.wallets.firstOrNull { it.id == state.walletId }
    Box {
        FilterChip(
            selected = current != null,
            onClick = { open = true },
            label = { Text(current?.name ?: "Semua dompet") },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Semua dompet") }, onClick = { actions.setWallet(null); open = false })
            state.wallets.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { actions.setWallet(w.id); open = false }) }
        }
    }
}

@Composable
private fun CategoryFilterChip(state: FinanceUiState, actions: FinanceActions) {
    var open by remember { mutableStateOf(false) }
    val f = state.txFilter
    val current = state.categories.firstOrNull { it.id == f.categoryId }
    Box {
        FilterChip(
            selected = current != null,
            onClick = { open = true },
            label = { Text(current?.name ?: "Semua kategori") },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Semua kategori") }, onClick = { actions.setTxFilter(f.copy(categoryId = null)); open = false })
            state.categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text((if (c.kind == CategoryKind.Income) "↓ " else "↑ ") + c.name) },
                    onClick = { actions.setTxFilter(f.copy(categoryId = c.id, kind = if (f.kind == TxKind.Transfer) null else f.kind)); open = false },
                )
            }
        }
    }
}
