package com.rosyidputrar.taskify.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.files.EntityType

/** Ikon shortcut bergaya kartu indeks: latar warna modul + inisial, sebagai ikon adaptif. */
internal object ShortcutIcons {
    fun letter(module: TaskifyModule, text: String = module.label): IconCompat {
        val size = 216 // 108dp × 2 (ikon adaptif, zona aman ~144px di tengah)
        val safe = size * 2 / 3f // 144px: isi ikon adaptif wajib muat di sini
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(module.light.toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 84f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val initial = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
        // Huruf lebar ("W") pada 84sp bisa melewati zona aman → kecilkan sampai muat di dalamnya.
        val bounds = android.graphics.Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)
        val scale = minOf(1f, safe / bounds.width().coerceAtLeast(1), safe / bounds.height().coerceAtLeast(1))
        if (scale < 1f) paint.textSize *= scale
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initial, size / 2f, y, paint)
        return IconCompat.createWithAdaptiveBitmap(bmp)
    }
}

/**
 * Shortcut launcher dinamis untuk item yang baru dibuka (catatan, jurnal, tugas) — tekan lama ikon
 * Taskify → langsung ke catatan terakhir. Judul tampil di launcher TANPA PIN, jadi tidak dibuat
 * selama kunci PIN aktif, dan semuanya dihapus saat PIN dinyalakan atau keluar akun.
 */
object EntityShortcuts {
    const val ACTION_OPEN_ENTITY = "com.rosyidputrar.taskify.action.OPEN_ENTITY"
    const val EXTRA_TYPE = "entity_type"
    const val EXTRA_ID = "entity_id"
    private const val PREFIX = "entity-"

    private fun moduleOf(type: EntityType) = when (type) {
        EntityType.Task -> TaskifyModule.Tasks
        EntityType.JournalEntry -> TaskifyModule.Journal
        EntityType.Note -> TaskifyModule.Notes
        EntityType.Transaction -> TaskifyModule.Finance
        EntityType.Snippet, EntityType.CodeProject -> TaskifyModule.Vault
    }

    fun report(context: Context, type: String, id: String, title: String, pinActive: Boolean) {
        if (pinActive) return
        val entity = EntityType.from(type) ?: return
        val module = moduleOf(entity)
        runCatching {
            val intent = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_ENTITY).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id)
            val info = ShortcutInfoCompat.Builder(context, "$PREFIX$type-$id")
                .setShortLabel(title.take(24))
                .setLongLabel("${module.label}: ${title.take(40)}")
                .setIcon(ShortcutIcons.letter(module, title))
                .setIntent(intent)
                .setRank(0)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, info)
        }
    }

    /** Hapus semua shortcut item (judul) — dipanggil saat PIN dinyalakan & saat keluar akun. */
    fun clear(context: Context) {
        runCatching {
            val ids = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.filter { it.startsWith(PREFIX) }
            if (ids.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
        }
    }

    fun entityOf(intent: Intent?): Pair<EntityType, String>? {
        if (intent?.action != ACTION_OPEN_ENTITY) return null
        val type = EntityType.from(intent.getStringExtra(EXTRA_TYPE)) ?: return null
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        return type to id
    }
}
