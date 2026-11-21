package com.rosyidputrar.taskify.feature.finance.component

import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.theme.NamedPalette
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.finance.ALERT_OPTIONS
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.FINANCE_ICON_NAMES
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.domain.tasks.describeRule
import com.rosyidputrar.taskify.core.domain.tasks.parseRule
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.finance.CategoryInput
import com.rosyidputrar.taskify.core.store.finance.DebtInput
import com.rosyidputrar.taskify.core.store.finance.RecurringInput
import com.rosyidputrar.taskify.core.store.finance.WalletInput
import com.rosyidputrar.taskify.core.ui.DatePickerSheet

/** Bottom sheet formulir dengan tombol Simpan (dan Hapus bila [onDelete] diisi). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = "Hapus",
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(deleteLabel, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Batal") }
                Button(onClick = onSave, enabled = saveEnabled) { Text("Simpan") }
            }
        }
    }
}

/** Label kecil di atas grup pilihan. */
@Composable
fun FieldLabel(text: String) = Text(text.uppercase(), style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Deretan chip pilihan tunggal (dompet, kategori, …). */
@Composable
fun <T> ChoiceRow(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onSelect: (T) -> Unit, leading: (@Composable (T) -> Unit)? = null) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(options) { o ->
            FilterChip(selected = selected(o), onClick = { onSelect(o) }, label = { Text(label(o)) }, leadingIcon = leading?.let { l -> { l(o) } })
        }
    }
}

/** Chip tanggal yang membuka pemilih tanggal. [allowClear] = tanggal opsional. */
@Composable
fun DateField(label: String, date: String?, today: String, onPick: (String?) -> Unit, allowClear: Boolean = false, placeholder: String = "Pilih tanggal") {
    var open by rememberSaveable { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text("$label: " + (date?.let { Dates.format(it, "d MMM yyyy") } ?: placeholder)) },
        leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp)) },
    )
    if (open) DatePickerSheet(date, today, allowClear = allowClear, onPick = { onPick(it); open = false }, onDismiss = { open = false })
}

// ===================================================================== Anggaran

@Composable
fun BudgetSheet(budget: BudgetStatus?, categories: List<Category>, usedCategoryIds: Set<String>, onDismiss: () -> Unit, onSave: (String?, Long, Int) -> Unit, onDelete: (() -> Unit)?) {
    var categoryId by rememberSaveable { mutableStateOf(budget?.categoryId ?: "") }
    var amount by rememberSaveable { mutableStateOf(moneyText(budget?.amount)) }
    var alert by rememberSaveable { mutableStateOf(budget?.alertPct ?: 80) }
    val parsed = Rupiah.parse(amount)
    val free = categories.filter { it.kind == CategoryKind.Expense && (budget != null || it.id !in usedCategoryIds) }
    val totalUsed = budget == null && "" in usedCategoryIds
    FormSheet(
        if (budget == null) "Anggaran baru" else "Ubah anggaran",
        onDismiss,
        onSave = { onSave(categoryId.ifEmpty { null }, parsed ?: 0, alert) },
        saveEnabled = parsed != null && parsed > 0 && !(budget == null && categoryId.isEmpty() && totalUsed),
        onDelete = onDelete,
    ) {
        FieldLabel("Untuk")
        if (budget != null) {
            Text(budget.name, style = MaterialTheme.typography.titleMedium)
        } else {
            val options = (if (totalUsed) emptyList() else listOf(null)) + free
            ChoiceRow(options, { (it?.id ?: "") == categoryId }, { it?.name ?: "Total semua pengeluaran" }, { categoryId = it?.id ?: "" },
                leading = { c -> c?.let { CategoryBadge(it.icon, it.color, size = 20.dp) } })
            if (totalUsed && categoryId.isEmpty()) Text("Anggaran total sudah ada — pilih kategori.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        MoneyField(amount, { amount = it }, "Batas per bulan", Modifier.fillMaxWidth())
        FieldLabel("Beri tanda saat terpakai")
        ChoiceRow(ALERT_OPTIONS, { it == alert }, { "$it%" }, { alert = it })
    }
}

// ===================================================================== Berulang

private val FREQS = listOf(RecurrenceFreq.Daily to "Harian", RecurrenceFreq.Weekly to "Mingguan", RecurrenceFreq.Monthly to "Bulanan", RecurrenceFreq.Yearly to "Tahunan")
private val REMIND_LEAD_OPTIONS: List<Pair<Long?, String>> = listOf(null to "Tidak", 1L to "H-1", 3L to "H-3", 7L to "H-7")

@Composable
fun RecurringSheet(
    item: Recurring?,
    wallets: List<Wallet>,
    categories: List<Category>,
    today: String,
    onDismiss: () -> Unit,
    onSave: (RecurringInput) -> Unit,
    onDelete: (() -> Unit)?,
    onSaveWithReminder: ((RecurringInput, Long?) -> Unit)? = null,
) {
    var kind by rememberSaveable { mutableStateOf(item?.kind ?: TxKind.Expense) }
    var title by rememberSaveable { mutableStateOf(item?.title ?: "") }
    var amount by rememberSaveable { mutableStateOf(moneyText(item?.amount)) }
    var walletId by rememberSaveable { mutableStateOf(item?.walletId ?: wallets.firstOrNull()?.id ?: "") }
    var toWalletId by rememberSaveable { mutableStateOf(item?.toWalletId ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(item?.categoryId ?: "") }
    var startOn by rememberSaveable { mutableStateOf(item?.startOn ?: today) }
    var endOn by rememberSaveable { mutableStateOf(item?.endOn) }
    var freq by rememberSaveable { mutableStateOf(item?.let { parseRule(it.rrule).freq.takeIf { f -> f != RecurrenceFreq.None && f != RecurrenceFreq.Weekdays } } ?: RecurrenceFreq.Monthly) }
    var interval by rememberSaveable { mutableStateOf(item?.let { parseRule(it.rrule).interval } ?: 1) }
    var autoPost by rememberSaveable { mutableStateOf(item?.autoPost ?: false) }
    var remindDaysBefore by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf(item?.note ?: "") }
    val parsed = Rupiah.parse(amount)
    // Mingguan ikut hari mulai; bulanan/tahunan ikut tanggal mulai (sama dengan webapp).
    val rule = buildRule(RecurrenceState(freq, interval, if (freq == RecurrenceFreq.Weekly) listOf(Dates.weekdayOf(startOn)) else emptyList()), startOn)
    val transferOk = kind != TxKind.Transfer || (toWalletId.isNotEmpty() && toWalletId != walletId)
    FormSheet(
        if (item == null) "Transaksi berulang" else "Ubah transaksi berulang",
        onDismiss,
        onSave = {
            val input = RecurringInput(
                item?.id, kind, parsed ?: 0, walletId, toWalletId.ifEmpty { null }, categoryId.ifEmpty { null }, title, note.ifBlank { null },
                rule!!, startOn, endOn, autoPost,
            )
            if (onSaveWithReminder != null) {
                onSaveWithReminder(input, if (kind == TxKind.Expense) remindDaysBefore else null)
            } else {
                onSave(input)
            }
        },
        saveEnabled = title.isNotBlank() && parsed != null && parsed > 0 && walletId.isNotEmpty() && transferOk && rule != null,
        onDelete = onDelete,
    ) {
        KindSelector(kind) { kind = it; categoryId = "" }
        OutlinedTextField(title, { title = it }, label = { Text("Nama") }, placeholder = { Text("mis. Netflix, Listrik, Gaji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MoneyField(amount, { amount = it }, "Nominal", Modifier.fillMaxWidth())
        FieldLabel(if (kind == TxKind.Transfer) "Dari dompet" else "Dompet")
        ChoiceRow(wallets, { it.id == walletId }, { it.name }, { walletId = it.id })
        if (kind == TxKind.Transfer) {
            FieldLabel("Ke dompet")
            ChoiceRow(wallets.filter { it.id != walletId }, { it.id == toWalletId }, { it.name }, { toWalletId = it.id })
        } else {
            FieldLabel("Kategori")
            val pool = categories.filter { it.kind.value == kind.value }
            ChoiceRow(listOf(null) + pool, { (it?.id ?: "") == categoryId }, { it?.name ?: "Tanpa kategori" }, { categoryId = it?.id ?: "" },
                leading = { c -> c?.let { CategoryBadge(it.icon, it.color, size = 20.dp) } })
        }
        FieldLabel("Jadwal")
        ChoiceRow(FREQS, { it.first == freq }, { it.second }, { freq = it.first })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Setiap", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(8.dp))
            ChoiceRow((1..4).toList(), { it == interval }, { "$it×" }, { interval = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Mulai", startOn, today, { it?.let { d -> startOn = d } })
            DateField("Sampai", endOn, today, { endOn = it }, allowClear = true, placeholder = "selamanya")
        }
        rule?.let { Text(describeRule(it, startOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.toggleable(autoPost, role = Role.Switch) { autoPost = it }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Catat otomatis", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (autoPost) "Dicatat sendiri saat jatuh tempo." else "Muncul di \"Jatuh tempo\" untuk dikonfirmasi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoPost, onCheckedChange = null)
        }
        if (kind == TxKind.Expense && onSaveWithReminder != null) {
            FieldLabel("Pengingat sebelum jatuh tempo")
            ChoiceRow(REMIND_LEAD_OPTIONS, { it.first == remindDaysBefore }, { it.second }, { remindDaysBefore = it.first })
            if (remindDaysBefore != null) {
                Text(
                    "Otomatis membuat tugas + alarm pukul 09:00 H-$remindDaysBefore untuk meninjau / membatalkan langganan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth())
    }
}

/** Pemilih jenis transaksi: Keluar · Masuk · Transfer. */
@Composable
fun KindSelector(kind: TxKind, onChange: (TxKind) -> Unit) {
    val options = listOf(TxKind.Expense to "Keluar", TxKind.Income to "Masuk", TxKind.Transfer to "Transfer")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (k, label) ->
            SegmentedButton(selected = kind == k, onClick = { onChange(k) }, shape = SegmentedButtonDefaults.itemShape(i, options.size), label = { FitText(label) })
        }
    }
}

// ===================================================================== Hutang

@Composable
fun DebtSheet(wallets: List<Wallet>, today: String, onDismiss: () -> Unit, onSave: (DebtInput) -> Unit) {
    var direction by rememberSaveable { mutableStateOf(DebtDirection.Lent) }
    var who by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var walletId by rememberSaveable { mutableStateOf("") }
    val parsed = Rupiah.parse(amount)
    FormSheet(
        "Catat hutang / piutang",
        onDismiss,
        onSave = { onSave(DebtInput(direction, who, parsed ?: 0, due, note.ifBlank { null }, walletId.ifEmpty { null })) },
        saveEnabled = who.isNotBlank() && parsed != null && parsed > 0,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DebtDirection.entries.forEachIndexed { i, d ->
                SegmentedButton(selected = direction == d, onClick = { direction = d }, shape = SegmentedButtonDefaults.itemShape(i, 2), label = { FitText(d.label) })
            }
        }
        OutlinedTextField(who, { who = it }, label = { Text(if (direction == DebtDirection.Lent) "Dipinjamkan ke" else "Pinjam dari") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MoneyField(amount, { amount = it }, "Nominal", Modifier.fillMaxWidth())
        DateField("Jatuh tempo", due, today, { due = it }, allowClear = true, placeholder = "tanpa")
        FieldLabel(if (direction == DebtDirection.Lent) "Uang keluar dari dompet" else "Uang masuk ke dompet")
        ChoiceRow(listOf(null) + wallets, { (it?.id ?: "") == walletId }, { it?.name ?: "Jangan catat" }, { walletId = it?.id ?: "" })
        OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth())
    }
}

/** Tandai lunas: pilih dompet untuk mencatat pelunasan, atau tanpa transaksi. */
@Composable
fun SettleDialog(debt: Debt, wallets: List<Wallet>, onDismiss: () -> Unit, onSettle: (String?) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(wallets.firstOrNull()?.id ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${debt.counterparty} lunas?") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    if (debt.direction == DebtDirection.Lent) "Uang ${Rupiah.format(debt.amount)} kembali ke dompet:" else "Bayar ${Rupiah.format(debt.amount)} dari dompet:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                (wallets.map { it.id to it.name } + ("" to "Jangan catat transaksi")).forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(choice == id, role = Role.RadioButton) { choice = id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == id, onClick = null)
                        Text(name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSettle(choice.ifEmpty { null }) }) { Text("Tandai lunas") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ===================================================================== Dompet & kategori

@Composable
fun WalletSheet(wallet: Wallet?, onDismiss: () -> Unit, onSave: (WalletInput) -> Unit, onArchive: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(wallet?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(wallet?.type ?: WalletType.Cash) }
    var initial by rememberSaveable { mutableStateOf(wallet?.initialBalance?.let { if (it == 0L) "0" else moneyText(it) } ?: "") }
    var color by rememberSaveable { mutableStateOf(wallet?.color ?: "emerald") }
    var icon by rememberSaveable { mutableStateOf(wallet?.icon ?: type.icon) }
    val parsed = if (initial.isBlank()) 0L else Rupiah.parse(initial)
    FormSheet(
        if (wallet == null) "Dompet baru" else "Ubah dompet",
        onDismiss,
        onSave = { onSave(WalletInput(name, type, parsed ?: 0, color, icon)) },
        saveEnabled = name.isNotBlank() && parsed != null,
        onDelete = onDelete,
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Nama") }, placeholder = { Text("mis. BCA, GoPay") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Jenis")
        ChoiceRow(WalletType.entries, { it == type }, { it.label }, { if (icon == type.icon) icon = it.icon; type = it })
        OutlinedTextField(
            initial, { initial = it }, label = { Text("Saldo awal") }, prefix = { Text("Rp ") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Saldo sebelum transaksi pertama dicatat di Taskify.") },
        )
        ColorPicker(color) { color = it }
        IconPicker(icon, color) { icon = it }
        if (onArchive != null) TextButton(onClick = onArchive) { Text(if (wallet?.archived == true) "Aktifkan lagi" else "Arsipkan dompet") }
    }
}

@Composable
fun CategorySheet(category: Category?, kind: CategoryKind, onDismiss: () -> Unit, onSave: (CategoryInput) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(category?.name ?: "") }
    var k by rememberSaveable { mutableStateOf(category?.kind ?: kind) }
    var color by rememberSaveable { mutableStateOf(category?.color ?: "orange") }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: "circle-ellipsis") }
    FormSheet(
        if (category == null) "Kategori baru" else "Ubah kategori",
        onDismiss,
        onSave = { onSave(CategoryInput(name, k, color, icon)) },
        saveEnabled = name.isNotBlank(),
        onDelete = onDelete,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBadge(icon, color, size = 48.dp)
            OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        if (category == null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CategoryKind.entries.forEachIndexed { i, c ->
                    SegmentedButton(selected = k == c, onClick = { k = c }, shape = SegmentedButtonDefaults.itemShape(i, 2), label = { FitText(c.label) })
                }
            }
        }
        ColorPicker(color) { color = it }
        IconPicker(icon, color) { icon = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    FieldLabel("Warna")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
        NamedPalette.names.forEach { name ->
            val c = namedColor(name)
            Box(
                Modifier.size(36.dp).background(c, CircleShape)
                    .then(if (name == selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                    .selectable(name == selected, role = Role.RadioButton) { onSelect(name) }
                    .semantics { contentDescription = "Warna $name" },
                contentAlignment = Alignment.Center,
            ) { if (name == selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, color: String, onSelect: (String) -> Unit) {
    FieldLabel("Ikon")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        FINANCE_ICON_NAMES.forEach { name ->
            val on = name == selected
            Box(
                Modifier.size(44.dp)
                    .background(if (on) namedColor(color).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .then(if (on) Modifier.border(2.dp, namedColor(color), CircleShape) else Modifier)
                    .selectable(on, role = Role.RadioButton) { onSelect(name) }
                    .semantics { contentDescription = "Ikon $name" },
                contentAlignment = Alignment.Center,
            ) { Icon(financeIcon(name), null, tint = if (on) namedColor(color) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
        }
    }
}

