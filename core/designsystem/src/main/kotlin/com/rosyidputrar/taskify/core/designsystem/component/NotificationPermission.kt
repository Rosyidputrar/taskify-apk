package com.rosyidputrar.taskify.core.designsystem.component

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Status izin notifikasi yang JUJUR: izin runtime (Android 13+), saklar notifikasi aplikasi, dan
 * kanal yang dimatikan pengguna semuanya dihitung.
 */
object NotificationAccess {
    fun allowed(context: Context, channelId: String? = null): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (channelId != null) {
            val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Pengaturan notifikasi Taskify di sistem (satu-satunya jalan setelah izin ditolak permanen). */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /**
     * Langkah berikut saat pengguna mengetuk "Izinkan". Android 13+ tidak lagi menampilkan dialog
     * setelah izin ditolak dua kali (`launch` langsung gagal tanpa apa-apa), jadi:
     * sudah pernah ditanya + sistem tidak memberi kesempatan lagi → buka pengaturan sistem.
     */
    fun nextStep(sdk: Int, granted: Boolean, appNotificationsOn: Boolean, askedBefore: Boolean, canShowRationale: Boolean): Step = when {
        sdk < 33 -> if (appNotificationsOn) Step.Nothing else Step.OpenSettings
        !granted && askedBefore && !canShowRationale -> Step.OpenSettings
        !granted -> Step.AskPermission
        !appNotificationsOn -> Step.OpenSettings
        else -> Step.Nothing
    }

    enum class Step { Nothing, AskPermission, OpenSettings }

    private const val PREFS = "taskify_permissions"
    private const val KEY_ASKED = "post_notifications_asked"

    internal fun askedBefore(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    internal fun markAsked(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
}

/**
 * Minta izin notifikasi dengan benar: dialog sistem bila masih bisa, pengaturan sistem bila izin
 * sudah ditolak permanen / notifikasi aplikasi dimatikan. [onResult] menerima status akhir.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(NotificationAccess.allowed(context)) }
    return remember(context, launcher) {
        {
            val activity = context.findActivityOrNull()
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val rationale = Build.VERSION.SDK_INT >= 33 && activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            when (
                NotificationAccess.nextStep(
                    Build.VERSION.SDK_INT, granted, NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    NotificationAccess.askedBefore(context), rationale,
                )
            ) {
                NotificationAccess.Step.AskPermission -> {
                    NotificationAccess.markAsked(context)
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                NotificationAccess.Step.OpenSettings -> NotificationAccess.openSettings(context)
                NotificationAccess.Step.Nothing -> onResult(true)
            }
        }
    }
}

/** Status izin notifikasi, diperbarui setiap layar kembali tampil (mis. setelah dari pengaturan sistem). */
@Composable
fun rememberNotificationsAllowed(channelId: String? = null): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NotificationAccess.allowed(context, channelId)) }
    LifecycleResumeEffect(context, channelId) {
        state.value = NotificationAccess.allowed(context, channelId)
        onPauseOrDispose { }
    }
    return state
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
