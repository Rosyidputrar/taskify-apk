package com.rosyidputrar.taskify

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals

/** Temuan 18: baca-ubah-tulis `notified` harus atomik walau dua worker jalan bersamaan. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContextReminderPrefsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: ContextReminderPrefs

    @Before fun setUp() {
        app.getSharedPreferences("taskify_device", 0).edit().clear().commit()
        prefs = ContextReminderPrefs(DevicePrefs(app))
    }

    @Test fun concurrentNotifiedUpdatesDoNotLoseKeys() {
        val threads = 10
        val perThread = 100
        val latch = CountDownLatch(threads)
        val errors = mutableListOf<Throwable>()
        repeat(threads) { t ->
            Thread {
                try {
                    repeat(perThread) { i -> prefs.updateNotified { it + "k-$t-$i" } }
                } catch (e: Throwable) {
                    synchronized(errors) { errors += e }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        latch.await()
        assertEquals(emptyList(), errors)
        assertEquals(threads * perThread, prefs.notified.size, "tidak ada kunci yang hilang karena race")
    }
}
