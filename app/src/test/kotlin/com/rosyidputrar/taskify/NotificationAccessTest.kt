package com.rosyidputrar.taskify

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.NotificationAccess
import com.rosyidputrar.taskify.core.designsystem.component.NotificationAccess.Step
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationAccessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun permanentlyDeniedGoesToSystemSettings() {
        // Pertama kali: dialog sistem.
        assertEquals(Step.AskPermission, NotificationAccess.nextStep(34, granted = false, appNotificationsOn = true, askedBefore = false, canShowRationale = false))
        // Ditolak sekali: sistem masih memberi kesempatan.
        assertEquals(Step.AskPermission, NotificationAccess.nextStep(34, granted = false, appNotificationsOn = true, askedBefore = true, canShowRationale = true))
        // Ditolak dua kali: dialog tidak akan muncul lagi → pengaturan.
        assertEquals(Step.OpenSettings, NotificationAccess.nextStep(34, granted = false, appNotificationsOn = true, askedBefore = true, canShowRationale = false))
        // Izin ada, tetapi notifikasi aplikasi dimatikan di pengaturan.
        assertEquals(Step.OpenSettings, NotificationAccess.nextStep(34, granted = true, appNotificationsOn = false, askedBefore = true, canShowRationale = false))
        assertEquals(Step.Nothing, NotificationAccess.nextStep(34, granted = true, appNotificationsOn = true, askedBefore = true, canShowRationale = false))
        // Sebelum Android 13 tidak ada izin runtime.
        assertEquals(Step.OpenSettings, NotificationAccess.nextStep(30, granted = true, appNotificationsOn = false, askedBefore = false, canShowRationale = false))
    }

    @Test fun allowedReflectsPermissionSwitchAndChannel() {
        val nm = app.getSystemService(NotificationManager::class.java)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(NotificationAccess.allowed(app))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(NotificationAccess.allowed(app))

        shadowOf(nm).setNotificationsEnabled(false)
        assertFalse(NotificationAccess.allowed(app), "saklar notifikasi aplikasi mati")
        shadowOf(nm).setNotificationsEnabled(true)

        nm.createNotificationChannel(NotificationChannel("task_reminders", "Pengingat", NotificationManager.IMPORTANCE_NONE))
        assertFalse(NotificationAccess.allowed(app, "task_reminders"), "kanal pengingat dimatikan")
        assertTrue(NotificationAccess.allowed(app))
    }
}
