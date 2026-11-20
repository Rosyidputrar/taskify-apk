package com.rosyidputrar.taskify.core.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.core.store.home.HomeShortcutRepository
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

/**
 * Item menu "Sematkan ke Hari ini" / "Lepas dari Hari ini" untuk layar isi (catatan, jurnal,
 * snippet, chat asisten, …). Beda dengan "Sematkan" di daftar modul: ini menaruh pintasan di
 * baris Pintasan Hari ini. Tanpa Koin (tes komponen) atau tanpa [id] (isi belum tersimpan),
 * item tidak ditampilkan.
 */
@Composable
fun TodayPinMenuItem(type: ShortcutType, id: String?, title: String, onDone: () -> Unit) {
    if (id.isNullOrBlank()) return
    val koin = remember { KoinPlatformTools.defaultContext().getOrNull() }
    val repo = remember(koin) { runCatching { koin?.getOrNull<HomeShortcutRepository>() }.getOrNull() } ?: return
    val messenger = remember(koin) { runCatching { koin?.getOrNull<Messenger>() }.getOrNull() }
    val ref = remember(type, id) { ShortcutRef(type, id) }
    val pinned by remember(repo, ref) { repo.watchIsPinned(ref) }.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    DropdownMenuItem(
        text = { Text(if (pinned) "Lepas dari Hari ini" else "Sematkan ke Hari ini") },
        leadingIcon = { Icon(Icons.Outlined.WbTwilight, null) },
        onClick = {
            scope.launch {
                val label = title.ifBlank { type.label }
                when (runCatching { repo.toggle(ref, label) }.getOrNull()) {
                    true -> messenger?.show("Disematkan ke Hari ini")
                    false -> messenger?.show("Dilepas dari Hari ini")
                    null -> messenger?.show("Maksimal ${HomeShortcuts.MAX_PINNED} pintasan di Hari ini. Lepas salah satu dulu.")
                }
                onDone()
            }
        },
    )
}
