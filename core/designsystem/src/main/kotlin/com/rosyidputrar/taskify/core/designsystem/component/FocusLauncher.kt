package com.rosyidputrar.taskify.core.designsystem.component

/**
 * Buka Mode Fokus dari layar fitur (detail tugas, dsb.) tanpa fitur perlu tahu navigasi aplikasi.
 * Aplikasi memasang [install] saat start; sebelum itu [available] = false dan tombolnya disembunyikan.
 */
object FocusLauncher {
    @Volatile
    private var sink: ((taskId: String?) -> Unit)? = null

    val available: Boolean get() = sink != null

    fun install(sink: (taskId: String?) -> Unit) {
        this.sink = sink
    }

    fun open(taskId: String?) {
        runCatching { sink?.invoke(taskId) }
    }
}
