package com.rosyidputrar.taskify.navigation

import android.net.Uri
import com.rosyidputrar.taskify.ui.shell.AppEvent

/**
 * Deep link `taskify://…` → [AppEvent], atau null bila bukan tautan Taskify yang dikenal.
 *
 * Polanya sama dengan intent internal aplikasi (lihat `TodayWidget`, `ReminderNotifier`):
 * `taskify://task/{id}` membuka tugas, `taskify://note/{id}` membuka catatan,
 * `taskify://journal/{id}` membuka entri jurnal; `?date=` pada note/journal membuka draf baru
 * di tanggal itu. Tautan yang tidak dikenal diabaikan (null) supaya aplikasi tidak crash
 * atau membuka layar yang salah — pemanggil ([MainActivity]) cukup tidak mengirim apa-apa.
 */
fun parseTaskifyDeepLink(uri: Uri): AppEvent? {
    if (uri.scheme != "taskify") return null
    val id = uri.lastPathSegment?.takeIf { it.isNotEmpty() }
    return when (uri.host) {
        "task" -> id?.let { AppEvent.OpenTask(it) }
        "note" -> when {
            id != null -> AppEvent.OpenNote(id = id)
            uri.getQueryParameter("date") != null -> AppEvent.OpenNote(date = uri.getQueryParameter("date"))
            else -> null
        }
        "journal" -> when {
            id != null -> AppEvent.OpenJournal(id = id)
            uri.getQueryParameter("date") != null -> AppEvent.OpenJournal(date = uri.getQueryParameter("date"))
            else -> null
        }
        else -> null
    }
}
