package com.rosyidputrar.taskify.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.search.Search
import com.rosyidputrar.taskify.core.domain.search.label
import com.rosyidputrar.taskify.core.domain.time.Dates

/** Modul pemilik jenis entitas (warna aksen & ikon lintas modul). */
val EntityType.module: TaskifyModule
    get() = when (this) {
        EntityType.Task -> TaskifyModule.Tasks
        EntityType.JournalEntry -> TaskifyModule.Journal
        EntityType.Note -> TaskifyModule.Notes
        EntityType.Transaction -> TaskifyModule.Finance
        EntityType.Snippet, EntityType.CodeProject -> TaskifyModule.Vault
    }

val EntityType.icon: ImageVector
    get() = when (this) {
        EntityType.Task -> Icons.Outlined.TaskAlt
        EntityType.JournalEntry -> Icons.Outlined.EditNote
        @Suppress("DEPRECATION")
        EntityType.Note -> Icons.Outlined.StickyNote2
        EntityType.Transaction -> Icons.Outlined.Wallet
        EntityType.Snippet -> Icons.Outlined.Code
        EntityType.CodeProject -> Icons.Outlined.FolderZip
    }

/** Keterangan singkat baris entitas: "Tugas · 12 Nov", "Transaksi · −Rp 25.000 · 3 Nov", "Snippet · kotlin". */
fun EntityRow.describe(): String {
    val sub = subtitle
    val tx = if (type == EntityType.Transaction) Search.txSubtitle(sub) else null
    val detail = when {
        tx != null -> listOf(
            (if (tx.kind == "expense") "−" else if (tx.kind == "income") "+" else "") + Rupiah.format(tx.amount),
            runCatching { Dates.format(tx.date, "d MMM") }.getOrDefault(tx.date),
        ).joinToString(" · ")
        sub.isNullOrBlank() -> null
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(sub) -> Dates.format(sub, "d MMM yyyy")
        else -> sub
    }
    return listOfNotNull(type.label, detail).joinToString(" · ")
}
