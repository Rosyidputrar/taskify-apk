package com.rosyidputrar.taskify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.auth.PinMac
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.designsystem.component.UiErrors
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.files.PickedFile
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.files.TempFilesApi
import com.rosyidputrar.taskify.core.files.Transfers
import com.rosyidputrar.taskify.core.reminders.ContextCheckWorker
import com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler
import com.rosyidputrar.taskify.core.reminders.JournalNudgeWorker
import com.rosyidputrar.taskify.core.store.NotSignedInException
import com.rosyidputrar.taskify.session.DeviceLeftovers
import com.rosyidputrar.taskify.session.LogoutIncompleteException
import com.rosyidputrar.taskify.session.LogoutStep
import com.rosyidputrar.taskify.session.runLogoutSteps
import com.rosyidputrar.taskify.ui.share.ShareInbox
import com.rosyidputrar.taskify.ui.shell.userMessageFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LogoutCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun initWorkManager() {
        // DeviceLeftovers.clear() membatalkan pekerjaan WorkManager; di Robolectric penyedia
        // startup androidx tidak jalan, jadi inisialisasi manual (sekali per sandbox tes).
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    @After fun reset() = UiErrors.install {}

    @Test fun errorInSafeLaunchIsReportedInsteadOfCrashing() = runBlocking {
        val reported = mutableListOf<Throwable>()
        UiErrors.install { reported += it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.launchSafe { throw NotSignedInException() }.join()
        assertIs<NotSignedInException>(reported.single())
        // Scope tetap hidup: coroutine berikutnya masih berjalan.
        var ran = false
        scope.launchSafe { ran = true }.join()
        assertTrue(ran)
    }

    @Test fun cancellationIsNotReported() = runBlocking {
        val reported = mutableListOf<Throwable>()
        UiErrors.install { reported += it }
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launchSafe { kotlinx.coroutines.delay(10_000) }
        job.cancel()
        job.join()
        assertTrue(reported.isEmpty())
    }

    @Test fun messagesHideTechnicalDetails() {
        assertTrue(userMessageFor(NotSignedInException()).startsWith("Belum masuk akun"))
        assertTrue(userMessageFor(RuntimeException("wrap", java.net.SocketTimeoutException())).startsWith("Gagal terhubung"))
        assertTrue(userMessageFor(LogoutIncompleteException(listOf("database"))).startsWith("Sudah keluar"))
        assertEquals("Terjadi kesalahan. Coba lagi.", userMessageFor(IllegalArgumentException("SELECT * FROM x")))
    }

    @Test fun logoutRunsEveryStepEvenWhenOneFails() = runBlocking {
        val ran = mutableListOf<String>()
        val failed = runLogoutSteps(
            listOf(
                LogoutStep("a") { ran += "a" },
                LogoutStep("database") { ran += "database"; error("SQLite sibuk") },
                LogoutStep("sesi") { ran += "sesi" },
            ),
        )
        assertEquals(listOf("a", "database", "sesi"), ran)
        assertEquals(listOf("database"), failed)
    }

    @Test fun leftoversAreClearedOnLogout() = runBlocking {
        val prefs = DevicePrefs(context)
        val lock = LockManager(prefs, PinMac { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 3 }, "HmacSHA256")) }.doFinal(it) })
        lock.enable("123456", 5)
        assertTrue(lock.state.value.config != null)

        val inbox = ShareInbox(context)
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        File(shareDir, "x.txt").writeText("rahasia")
        val uploadCopy = File(context.cacheDir, DeviceLeftovers.SHARE_UPLOAD_DIR).apply { mkdirs() }.let { File(it, "k") }.apply { writeText("isi") }

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("t", "t", NotificationManager.IMPORTANCE_LOW))
        nm.notify(7, NotificationCompat.Builder(context, "t").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Bayar listrik").build())
        assertEquals(1, shadowOf(nm).allNotifications.size)

        DeviceLeftovers(context, inbox, lock).clear()

        assertNull(lock.state.value.config)
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
        assertFalse(uploadCopy.exists())
        assertTrue(shareDir.listFiles().orEmpty().isEmpty())
        assertNull(inbox.content.value)
    }

    @Test fun logoutCancelsContextualWork() = runBlocking {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            ContextReminderScheduler.WORK_CHECK, androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ContextCheckWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniqueWork(
            ContextReminderScheduler.WORK_JOURNAL, androidx.work.ExistingWorkPolicy.REPLACE,
            // Ditunda 10 jam supaya worker tidak sempat jalan selama tes (butuh Koin + database).
            OneTimeWorkRequestBuilder<JournalNudgeWorker>().setInitialDelay(10, TimeUnit.HOURS).build(),
        )
        assertTrue(wm.getWorkInfosForUniqueWork(ContextReminderScheduler.WORK_CHECK).get().isNotEmpty())
        assertTrue(wm.getWorkInfosForUniqueWork(ContextReminderScheduler.WORK_JOURNAL).get().isNotEmpty())

        val prefs = DevicePrefs(context)
        val lock = LockManager(prefs, PinMac { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 3 }, "HmacSHA256")) }.doFinal(it) })
        DeviceLeftovers(context, ShareInbox(context), lock).clear()

        val check = wm.getWorkInfosForUniqueWork(ContextReminderScheduler.WORK_CHECK).get()
        val journal = wm.getWorkInfosForUniqueWork(ContextReminderScheduler.WORK_JOURNAL).get()
        assertTrue(check.isNotEmpty() && check.all { it.state == WorkInfo.State.CANCELLED }, "context-check harus dibatalkan, dapat $check")
        assertTrue(journal.isNotEmpty() && journal.all { it.state == WorkInfo.State.CANCELLED }, "journal-nudge harus dibatalkan, dapat $journal")
    }

    @Test fun logoutInvokesContextWorkCanceller() = runBlocking {
        var calls = 0
        val prefs = DevicePrefs(context)
        val lock = LockManager(prefs, PinMac { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 3 }, "HmacSHA256")) }.doFinal(it) })
        DeviceLeftovers(context, ShareInbox(context), lock, cancelContextWork = { calls++ }).clear()
        assertEquals(1, calls)
    }

    @Test fun ownCacheCopyIsDeletedAfterUploadEnds() {
        // Server tidak dikonfigurasi → pemesanan gagal; salinan sementara tetap harus dibuang.
        val copy = File(context.cacheDir, DeviceLeftovers.SHARE_UPLOAD_DIR).apply { mkdirs() }.let { File(it, "u1") }.apply { writeText("isi") }
        val outside = File(context.filesDir, "milik-pengguna.txt").apply { writeText("jangan dihapus") }
        val uploader = TempFileUploader(context, TempFilesApi(null, ""), Transfers(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        uploader.start(PickedFile(Uri.fromFile(copy), "u1", copy.length(), "text/plain"), ExpiryOption.DEFAULT)
        uploader.start(PickedFile(Uri.fromFile(outside), "o", outside.length(), "text/plain"), ExpiryOption.DEFAULT)
        assertFalse(copy.exists())
        assertTrue(outside.exists())
    }
}
