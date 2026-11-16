package com.rosyidputrar.taskify.feature.finance

import com.rosyidputrar.taskify.core.designsystem.component.LoadingScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.ui.DatePickerSheet
import com.rosyidputrar.taskify.core.ui.TimePickerDialog
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.feature.attachments.AttachmentsSection
import com.rosyidputrar.taskify.feature.links.LinksSection
import com.rosyidputrar.taskify.feature.finance.component.CategoryBadge
import com.rosyidputrar.taskify.feature.finance.component.ChoiceRow
import com.rosyidputrar.taskify.feature.finance.component.FieldLabel
import com.rosyidputrar.taskify.feature.finance.component.KindSelector
import com.rosyidputrar.taskify.feature.finance.component.expenseColor
import com.rosyidputrar.taskify.feature.finance.component.incomeColor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TxEditorScreen(args: TxEditorArgs, onBack: () -> Unit) {
    val vm: TxEditorViewModel = koinViewModel(key = "tx-$args") { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    TxEditorContent(
        state, Dates.today(),
        object : TxEditorActions {
            override fun back() = onBack()
            override fun update(block: (TxDraft) -> TxDraft) = vm.update(block)
            override fun kind(kind: TxKind) = vm.setKind(kind)
            override fun add(delta: Long) = vm.addAmount(delta)
            override fun save() = vm.save(onBack)
            override fun remove() = vm.remove(onBack)
        },
        attachments = { id -> AttachmentsSection(EntityType.Transaction, id); LinksSection(EntityType.Transaction, id) },
    )
}

interface TxEditorActions {
    fun back()
    fun update(block: (TxDraft) -> TxDraft)
    fun kind(kind: TxKind)
    fun add(delta: Long)
    fun save()
    fun remove()
}

private val QUICK = listOf(5_000L, 10_000L, 50_000L, 100_000L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxEditorContent(state: TxEditorState, today: String, actions: TxEditorActions, attachments: @Composable (txId: String) -> Unit = {}) {
    val d = state.draft
    if (!d.loaded) {
        LoadingScreen(module = com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Finance)
        return
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
            Text(if (d.id == null) "Catat transaksi" else "Ubah transaksi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (d.id != null) IconButton(onClick = actions::remove) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus transaksi") }
        }
        if (d.missing) {
            EmptyState(TaskifyModule.Finance, "Transaksi tidak ditemukan", "Mungkin sudah dihapus atau belum tersinkron ke perangkat ini.")
            return
        }
        var pickDate by rememberSaveable { mutableStateOf(false) }
        var pickTime by rememberSaveable { mutableStateOf(false) }
        Column(
            Modifier.weight(1f).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IndexTab(TaskifyModule.Finance)
            KindSelector(d.kind, actions::kind)
            AmountHero(d, actions)
            if (d.kind == TxKind.Transfer) {
                FieldLabel("Dari dompet")
                ChoiceRow(state.wallets, { it.id == d.walletId }, { it.name }, { w -> actions.update { it.copy(walletId = w.id, toWalletId = if (it.toWalletId == w.id) null else it.toWalletId) } })
                FieldLabel("Ke dompet")
                ChoiceRow(state.wallets.filter { it.id != d.walletId }, { it.id == d.toWalletId }, { it.name }, { w -> actions.update { it.copy(toWalletId = w.id) } })
            } else {
                FieldLabel("Kategori")
                val pool = state.categories.filter { it.kind.value == d.kind.value }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pool.forEach { c -> CategoryTile(c, c.id == d.categoryId) { actions.update { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) } } }
                    if (pool.isEmpty()) Text("Belum ada kategori ${d.kind.label.lowercase()}. Tambahkan di tab Kelola.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                FieldLabel("Dompet")
                ChoiceRow(state.wallets, { it.id == d.walletId }, { it.name }, { w -> actions.update { it.copy(walletId = w.id) } },
                    leading = { w -> CategoryBadge(w.icon ?: w.type.icon, w.color, size = 20.dp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { pickDate = true }, label = { Text(Dates.relativeDayLabel(d.date, today)) }, leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp)) })
                AssistChip(onClick = { pickTime = true }, label = { Text(d.time ?: "Tanpa jam") }, leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp)) })
            }
            OutlinedTextField(d.title, { v -> actions.update { it.copy(title = v) } }, label = { Text("Keterangan (opsional)") }, placeholder = { Text("mis. Makan siang bareng tim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(d.note, { v -> actions.update { it.copy(note = v) } }, label = { Text("Catatan (opsional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            d.linked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            d.id?.let { attachments(it) }
            Spacer(Modifier.size(8.dp))
        }
        Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                d.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp)) }
                Button(onClick = actions::save, modifier = Modifier.fillMaxWidth()) {
                    Text(if (d.id == null) "Simpan transaksi" else "Simpan perubahan")
                }
            }
        }
        if (pickDate) DatePickerSheet(d.date, today, allowClear = false, onPick = { p -> p?.let { v -> actions.update { it.copy(date = v) } }; pickDate = false }, onDismiss = { pickDate = false })
        if (pickTime) TimePickerDialog(d.time, onPick = { t -> actions.update { it.copy(time = t) }; pickTime = false }, onDismiss = { pickTime = false })
    }
}

/** Nominal besar di tengah: ketik bebas ("25rb", "1,5jt"), hasil bacaan tampil di bawahnya. */
@Composable
private fun AmountHero(d: TxDraft, actions: TxEditorActions) {
    val colors = MaterialTheme.colorScheme
    val tone = when (d.kind) { TxKind.Income -> incomeColor(); TxKind.Expense -> expenseColor(); TxKind.Transfer -> colors.onSurface }
    val parsed = d.amount
    val style = TaskifyTextStyles.numeric.copy(color = tone, textAlign = TextAlign.Center, fontSize = TaskifyTextStyles.numeric.fontSize * 1.2f)
    Surface(color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (d.kind == TxKind.Income) "MASUK" else if (d.kind == TxKind.Expense) "KELUAR" else "PINDAH", style = TaskifyTextStyles.indexLabel, color = tone)
            BasicTextField(
                value = d.amountText,
                onValueChange = { v -> actions.update { it.copy(amountText = v) } },
                textStyle = style,
                singleLine = true,
                cursorBrush = SolidColor(tone),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nominal" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (d.amountText.isEmpty()) Text("Rp 0", style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth())
                        inner()
                    }
                },
            )
            Text(
                when {
                    d.amountText.isBlank() -> "Ketik nominal: 25000, 25rb, 1,5jt"
                    parsed != null && parsed > 0 -> "= ${Rupiah.format(parsed)}"
                    Rupiah.isIncomplete(d.amountText) -> "…"
                    else -> "Nominal tidak valid"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (d.amountText.isNotBlank() && parsed == null && !Rupiah.isIncomplete(d.amountText)) colors.error else colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QUICK.forEach { q -> SuggestionChip(onClick = { actions.add(q) }, label = { Text("+" + Rupiah.compact(q)) }) }
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Category, selected: Boolean, onClick: () -> Unit) {
    val tint = namedColor(c.color)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, tint) else null,
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryBadge(c.icon, c.color, size = 28.dp)
            Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
