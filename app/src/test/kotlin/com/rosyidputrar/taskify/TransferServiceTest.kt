package com.rosyidputrar.taskify

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.files.TransferService
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.files.TempFilesApi
import com.rosyidputrar.taskify.core.files.Transfers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TransferServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun koin() {
        stopKoin()
        startKoin {
            modules(module {
                single { TempFileUploader(context, TempFilesApi(null, ""), Transfers(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) }
            })
        }
    }

    @After fun tearDown() { stopKoin() }

    @Test fun goesForegroundThenStopsWhenNothingIsTransferring() {
        val controller = Robolectric.buildService(TransferService::class.java, Intent(context, TransferService::class.java)).create().startCommand(0, 1)
        val shadow = shadowOf(controller.get())
        // Kanal notifikasi "transfers" dibuat untuk notifikasi foreground.
        assertNotNull(context.getSystemService(NotificationManager::class.java).getNotificationChannel(TransferService.CHANNEL_ID))
        // Tidak ada unggahan/unduhan aktif → berhenti sendiri dan notifikasi dibuang.
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(shadow.isStoppedBySelf, "layanan harus berhenti sendiri saat tidak ada transfer")
        // isForegroundStopped hanya true bila layanan SEMPAT startForeground (wajib < 5 dtk).
        assertTrue(shadow.isForegroundStopped, "layanan harus sempat masuk foreground")
    }

    @Test fun cancelActionDoesNotCrash() {
        val intent = Intent(context, TransferService::class.java).setAction(TransferService.ACTION_CANCEL)
        Robolectric.buildService(TransferService::class.java, intent).create().startCommand(0, 1)
    }
}
