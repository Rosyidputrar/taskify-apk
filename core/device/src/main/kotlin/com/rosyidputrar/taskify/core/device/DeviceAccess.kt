package com.rosyidputrar.taskify.core.device

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Status & jalan pintas izin khusus kedua modul (keduanya diberikan pengguna di Setelan sistem). */
object DeviceAccess {
    fun notificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun usageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * APK yang dipasang di luar Play Store (mis. dari GitHub) di Android 13+ harus lebih dulu
     * mengizinkan "setelan terbatas" sebelum Akses notifikasi bisa dinyalakan.
     */
    fun needsRestrictedSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer != "com.android.vending"
    }

    fun listenerComponent(context: Context) = ComponentName(context, TaskifyNotificationListener::class.java)

    fun notificationSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun usageSettings(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:" + context.packageName))

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + context.packageName))

    /** Buka [intent]; bila halaman spesifik tidak ada di HP ini, buka [fallback]. */
    fun open(context: Context, intent: Intent, fallback: Intent? = null) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { context.startActivity(intent.addFlags(flags)) }
            .recoverCatching { context.startActivity((fallback ?: Intent(intent.action)).addFlags(flags)) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags)) }
    }

    /**
     * Batalkan requestUnbind sebelumnya. Sistem MENGABAIKAN panggilan ini bila pendengar tidak
     * sedang dilepas lewat requestUnbind — untuk pendengar yang mati, pakai [reconnectListener].
     */
    fun rebindListener(context: Context) {
        runCatching { NotificationListenerService.requestRebind(listenerComponent(context)) }
    }

    /**
     * Sambung ulang paksa (mis. setelah proses dimatikan penghemat baterai): lepas dulu, baru
     * sambungkan, supaya sistem benar-benar mengikat ulang. Jangan dipanggil dari
     * onListenerDisconnected — pelepasan memicu callback itu lagi (berputar tanpa henti).
     * Sengaja TIDAK mematikan-menyalakan komponen: sistem mencabut Akses notifikasi dari komponen
     * yang sedang nonaktif. Di bawah Android 14 pelepasan lewat komponen belum ada, jadi hanya
     * requestRebind.
     */
    fun reconnectListener(context: Context) {
        val component = listenerComponent(context)
        if (Build.VERSION.SDK_INT >= 34) runCatching { NotificationListenerService.requestUnbind(component) }
        runCatching { NotificationListenerService.requestRebind(component) }
    }

    /** Merek HP yang dikenal agresif mematikan aplikasi di latar → tampilkan tips Autostart. */
    fun aggressiveOem(): String? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi"
        "oppo", "realme", "oneplus" -> Build.MANUFACTURER.replaceFirstChar(Char::uppercase)
        "vivo", "iqoo" -> "Vivo"
        "huawei", "honor" -> Build.MANUFACTURER.replaceFirstChar(Char::uppercase)
        "samsung" -> "Samsung"
        else -> null
    }
}

/** Status izin untuk ViewModel (bisa dipalsukan di tes). */
interface AccessProbe {
    fun notifications(): Boolean
    fun usage(): Boolean
    fun restrictedSettingsHint(): Boolean
    fun oem(): String?
}

class AndroidAccessProbe(private val context: android.content.Context) : AccessProbe {
    override fun notifications() = DeviceAccess.notificationAccess(context)
    override fun usage() = DeviceAccess.usageAccess(context)
    override fun restrictedSettingsHint() = DeviceAccess.needsRestrictedSettings(context)
    override fun oem() = DeviceAccess.aggressiveOem()
}
