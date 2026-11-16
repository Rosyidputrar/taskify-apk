package com.rosyidputrar.taskify.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule

/**
 * Shortcut dinamis launcher: modul yang dibuka dilaporkan, sistem menampilkan yang paling sering
 * dipakai saat ikon Taskify ditekan lama (di samping Tangkap, Cari, Kalender).
 */
object ModuleShortcuts {
    const val ACTION_OPEN_MODULE = "com.rosyidputrar.taskify.action.OPEN_MODULE"
    const val EXTRA_MODULE = "module"

    /** Beranda & Kalender sudah ada di dok/shortcut statis. */
    private val skip = setOf(TaskifyModule.Home, TaskifyModule.Calendar)

    fun report(context: Context, module: TaskifyModule) {
        if (module in skip) return
        runCatching {
            val intent = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_MODULE).putExtra(EXTRA_MODULE, module.name)
            val info = ShortcutInfoCompat.Builder(context, "module-${module.name}")
                .setShortLabel(module.label)
                .setLongLabel("Buka ${module.label}")
                .setIcon(ShortcutIcons.letter(module))
                .setIntent(intent)
                .setRank(1)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, info)
        }
    }

    fun moduleOf(intent: Intent?): TaskifyModule? =
        intent?.takeIf { it.action == ACTION_OPEN_MODULE }?.getStringExtra(EXTRA_MODULE)?.let { n -> TaskifyModule.entries.firstOrNull { it.name == n } }
}
