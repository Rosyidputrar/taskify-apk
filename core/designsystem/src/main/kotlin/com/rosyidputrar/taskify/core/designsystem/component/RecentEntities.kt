package com.rosyidputrar.taskify.core.designsystem.component

/**
 * Laporan "item ini baru dibuka" dari layar fitur ke aplikasi (yang mengubahnya jadi shortcut
 * launcher dinamis). Fitur tidak perlu tahu soal launcher; aplikasi memasang [install] saat start.
 * [type] = nilai EntityType ("note", "journal_entry", "task", …).
 */
object RecentEntities {
    @Volatile
    private var sink: (type: String, id: String, title: String) -> Unit = { _, _, _ -> }

    fun install(sink: (type: String, id: String, title: String) -> Unit) {
        this.sink = sink
    }

    fun report(type: String, id: String, title: String) {
        if (id.isBlank() || title.isBlank()) return
        runCatching { sink(type, id, title.trim()) }
    }
}
