package com.rosyidputrar.taskify.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/** Nama, ikon, dan peluncur aplikasi lain (dengan cache). */
class AppInfo(private val context: Context) {
    private val pm get() = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = object : LruCache<String, Bitmap>(64) {}
    @Volatile private var launcherCache: Set<String>? = null

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').replaceFirstChar(Char::uppercase)
    }

    /** Ikon aplikasi (null bila tidak terpasang lagi). */
    fun icon(pkg: String, sizePx: Int = 96): Bitmap? {
        icons.get(pkg)?.let { return it }
        val bmp = runCatching { pm.getApplicationIcon(pkg).toBitmap(sizePx, sizePx) }.getOrNull() ?: return null
        icons.put(pkg, bmp)
        return bmp
    }

    fun isInstalled(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Launcher (layar utama) — tidak dihitung sebagai pemakaian aplikasi. */
    fun launchers(): Set<String> = launcherCache ?: run {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val set = runCatching { pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }.toSet() }.getOrDefault(emptySet())
        set.also { launcherCache = it }
    }

    /** Kategori sistem (Sosial, Video, Game, …) bila aplikasi menyatakannya. */
    fun category(pkg: String): String? {
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Foto"
            ApplicationInfo.CATEGORY_SOCIAL -> "Sosial"
            ApplicationInfo.CATEGORY_NEWS -> "Berita"
            ApplicationInfo.CATEGORY_MAPS -> "Peta"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produktivitas"
            else -> null
        }
    }

    companion object {
        /** Paket sistem yang bukan "pemakaian aplikasi". */
        val SYSTEM_UI = setOf("android", "com.android.systemui", "com.google.android.permissioncontroller", "com.android.permissioncontroller")
    }
}
