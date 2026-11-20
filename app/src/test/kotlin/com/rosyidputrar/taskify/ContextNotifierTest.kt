package com.rosyidputrar.taskify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.reminders.ContextNotifier
import com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs
import com.rosyidputrar.taskify.core.reminders.ReminderPrefs
import com.rosyidputrar.taskify.widget.ModuleShortcuts
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notifikasi tagihan / anggaran / jurnal: kanal sendiri, aksi Catat/Lewati, isi bisa disembunyikan. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContextNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private lateinit var reminderPrefs: ReminderPrefs
    private lateinit var notifier: ContextNotifier

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        reminderPrefs = ReminderPrefs(DevicePrefs(app))
        reminderPrefs.hideContent = false
        notifier = ContextNotifier(app, reminderPrefs)
    }

    private val netflix = Recurring("r1", TxKind.Expense, 54_990, "w1", null, null, "Netflix", null, "FREQ=MONTHLY;INTERVAL=1", "2026-01-01", null, "2026-11-16", false, true)

    /** D-13: bawaan baru = isi disembunyikan; pengguna lama yang sudah menyimpan pilihan tidak berubah. */
    @Test fun hideContentDefaultsToHidden() {
        app.getSharedPreferences("taskify_device", 0).edit().clear().commit()
        val fresh = ReminderPrefs(DevicePrefs(app))
        assertTrue(fresh.hideContent)
        fresh.hideContent = false
        assertFalse(ReminderPrefs(DevicePrefs(app)).hideContent, "pilihan pengguna yang disimpan harus dihormati")
    }

    @Test fun billHasPostAndSkipActionsAndOpensFinance() {
        notifier.bill(netflix, "2026-11-16")
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(ContextNotifier.CHANNEL_BILLS, n.channelId)
        assertTrue(n.extras.getString("android.title")!!.contains("Netflix"))
        assertEquals(listOf("Catat", "Lewati"), n.actions.map { it.title.toString() })
        val open = shadowOf(n.contentIntent).savedIntent
        assertEquals(TaskifyModuleName.Finance, ModuleShortcuts.moduleOf(open)?.name)
    }

    @Test fun hiddenContentHidesTitleAndAmount() {
        reminderPrefs.hideContent = true
        notifier.bill(netflix, "2026-11-16")
        val title = shadowOf(nm).allNotifications.single().extras.getString("android.title")!!
        assertFalse(title.contains("Netflix"))
        assertFalse(title.contains("54"))
    }

    @Test fun budgetAndJournalUseTheirOwnChannels() {
        notifier.budget(BudgetStatus("b1", null, 1_000_000, 80, "2026-11-01", null, "Makan", null, null, 1_050_000))
        notifier.journal(streak = 5)
        val byChannel = shadowOf(nm).allNotifications.associateBy { it.channelId }
        assertTrue(byChannel.getValue(ContextNotifier.CHANNEL_BUDGETS).extras.getString("android.title")!!.contains("terlampaui"))
        assertTrue(byChannel.getValue(ContextNotifier.CHANNEL_JOURNAL).extras.getString("android.title")!!.contains("5 hari"))
        assertNotNull(nm.getNotificationChannel(ContextNotifier.CHANNEL_JOURNAL))
    }

    @Test fun hiddenContentHidesJournalStreakAndAutoPostedCount() {
        reminderPrefs.hideContent = true
        notifier.journal(streak = 5)
        notifier.autoPosted(3)
        val byChannel = shadowOf(nm).allNotifications.associateBy { it.channelId }
        val journal = byChannel.getValue(ContextNotifier.CHANNEL_JOURNAL)
        assertEquals("Pengingat jurnal", journal.extras.getString("android.title"))
        assertFalse((journal.extras.getString("android.title") ?: "").contains("5"))
        assertNull(journal.extras.getString("android.text"))
        val auto = byChannel.getValue(ContextNotifier.CHANNEL_BILLS)
        assertFalse((auto.extras.getString("android.title") ?: "").contains("3"))
        assertNull(auto.extras.getString("android.text"))
    }

    @Test fun prefsDefaultsAndHistoryReset() {
        val p = ContextReminderPrefs(DevicePrefs(app))
        assertTrue(p.bills && p.budgets && p.journal)
        assertEquals("20:00", p.journalTime)
        p.notified = setOf("r1|2026-11-16")
        p.forgetHistory()
        assertTrue(p.notified.isEmpty())
        assertTrue(p.bills, "saklar milik perangkat tidak ikut terhapus")
    }
}

private object TaskifyModuleName { const val Finance = "Finance" }
