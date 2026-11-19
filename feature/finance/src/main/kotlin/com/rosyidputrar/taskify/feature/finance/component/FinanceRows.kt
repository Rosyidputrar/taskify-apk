package com.rosyidputrar.taskify.feature.finance.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.time.Dates

/** Nominal bertanda untuk tampilan: +Rp untuk masuk, −Rp untuk keluar, tanpa tanda untuk transfer. */
fun signedLabel(kind: TxKind, amount: Long): String = when (kind) {
    TxKind.Income -> "+" + Rupiah.format(amount)
    TxKind.Expense -> "−" + Rupiah.format(amount)
    TxKind.Transfer -> Rupiah.format(amount)
}

@Composable
fun amountColor(kind: TxKind): Color = when (kind) {
    TxKind.Income -> incomeColor()
    TxKind.Expense -> MaterialTheme.colorScheme.onSurface
    TxKind.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Ikon depan transaksi: ikon aplikasi asal bila dicatat dari notifikasi (kategori tetap terlihat
 * sebagai lencana kecil di pojok), selain itu ikon kategori.
 */
@Composable
fun TxLeading(tx: Transaction, size: Dp = 40.dp) {
    val pkg = tx.sourcePackage
    if (pkg == null) {
        CategoryBadge(tx.categoryIcon, tx.categoryColor, size = size, transfer = tx.kind == TxKind.Transfer)
        return
    }
    Box(Modifier.size(size)) {
        AppIcon(pkg, tx.sourceLabel ?: "", size = size)
        if (tx.categoryId != null || tx.kind == TxKind.Transfer) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(size * 0.5f)
                    .background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp),
            ) {
                CategoryBadge(tx.categoryIcon, tx.categoryColor, size = size * 0.5f - 4.dp, transfer = tx.kind == TxKind.Transfer)
            }
        }
    }
}

/** Baris transaksi; geser ke kiri untuk menghapus (dengan Urungkan). */
@Composable
fun TxRow(tx: Transaction, onOpen: () -> Unit, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    val meta = buildList {
        Dates.shortTime(tx.occurredTime).takeIf { it.isNotEmpty() }?.let(::add)
        if (tx.kind == TxKind.Transfer) add("${tx.walletName ?: "?"} → ${tx.toWalletName ?: "?"}") else {
            if (tx.title != null) tx.categoryName?.let(::add)
            tx.walletName?.let(::add)
        }
        if (tx.debtId != null) add("hutang")
    }.joinToString(" · ")
    val description = "${tx.displayTitle}, ${signedLabel(tx.kind, tx.amount)}, $meta" + (tx.sourceLabel?.let { ", dari $it" } ?: "")
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClickLabel = "Ubah transaksi", onClick = onOpen)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (onDelete != null) customActions = listOf(CustomAccessibilityAction("Hapus") { onDelete(); true })
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TxLeading(tx)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tx.displayTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (tx.recurringId != null) Icon(Icons.Outlined.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(signedLabel(tx.kind, tx.amount), style = MaterialTheme.typography.titleSmall, color = amountColor(tx.kind))
        }
    }
    if (onDelete == null) {
        Box(modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState()
    val haptics = rememberHaptics()
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) haptics.threshold()
    }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

/** Kartu dompet di karusel: ikon, nama, saldo besar; garis tepi tebal saat dipakai sebagai filter. */
@Composable
fun WalletCard(wallet: Wallet, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = namedColor(wallet.color)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = TaskifyTheme.modules.tint(TaskifyModule.Finance, MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(2.dp, accent) else null,
        modifier = modifier.width(176.dp).semantics {
            contentDescription = "${wallet.name}, saldo ${Rupiah.format(wallet.balance)}" + if (selected) ", dipilih sebagai filter" else ""
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(wallet.icon ?: wallet.type.icon, wallet.color, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(wallet.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(wallet.type.label.uppercase(), style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FitText(
                Rupiah.format(wallet.balance),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
                color = if (wallet.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = 0.6f)))
        }
    }
}
