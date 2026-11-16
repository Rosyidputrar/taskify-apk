package com.rosyidputrar.taskify

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SandiTab
import com.rosyidputrar.taskify.ui.gate.RecentsPrivacy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun spacedCodeForTest(code: String) = code.substring(0, code.length / 2) + " " + code.substring(code.length / 2)

/** "Ingat perangkat" butuh verifikasi pemilik HP; detak kode 2FA tidak menyusun ulang seluruh daftar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiTrustUiTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : SandiActions by SampleSandi.Actions {
        var trusted = 0
        var unlockedWith: Pair<String, Boolean>? = null
        override fun tryTrusted() { trusted++ }
        override fun unlock(password: String, remember: Boolean) { unlockedWith = password to remember }
    }

    @Test fun rememberedDeviceOffersBiometricUnlock() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { SandiContent(SampleSandi.state("locked"), actions, PaddingValues()) } }
        compose.waitForIdle()
        assertEquals(1, actions.trusted, "dicoba sekali otomatis saat panel tampil")
        compose.onNodeWithText("Buka dengan sidik jari / kunci layar").performScrollTo().performClick()
        assertEquals(2, actions.trusted)
    }

    @Test fun withoutScreenLockRememberingIsDisabled() {
        val actions = Recorder()
        val state = SampleSandi.state("locked").copy(trustAvailable = false, trustDaysLeft = null)
        compose.setContent { TaskifyTheme { SandiContent(state, actions, PaddingValues()) } }
        compose.onNodeWithText("Pasang kunci layar", substring = true).assertExists()
        compose.onNodeWithText("Buka dengan sidik jari / kunci layar").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("rahasia")
        compose.onNodeWithText("Ingat perangkat ini 30 hari").performClick()
        compose.onNodeWithText("Buka brankas").performClick()
        assertEquals("rahasia" to false, actions.unlockedWith)
    }

    @Test fun rememberToggleIsOneTouchTarget() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { SandiContent(SampleSandi.state("locked"), actions, PaddingValues()) } }
        compose.onNode(hasSetTextAction()).performTextInput("rahasia")
        compose.onNodeWithText("Ingat perangkat ini 30 hari").performClick()
        compose.onNodeWithText("Buka brankas").performClick()
        assertEquals("rahasia" to true, actions.unlockedWith)
    }

    @Test fun codesFollowTheClockWithoutNewState() {
        val clock = mutableLongStateOf(SampleSandi.NOW)
        val state = SampleSandi.state(tab = SandiTab.Codes)
        compose.setContent { TaskifyTheme { SandiContent(state, SampleSandi.Actions, PaddingValues(), clock = clock) } }
        val cfg = SampleSandi.items[0].payload.totp!!
        val first = Totp.window(cfg, SampleSandi.NOW)!!.code
        compose.onNodeWithText(spacedCodeForTest(first)).assertExists()
        // Periode berikutnya: state tidak berubah, hanya jam.
        val later = SampleSandi.NOW + 30_000
        val next = Totp.window(cfg, later)!!.code
        clock.longValue = later
        compose.waitForIdle()
        compose.onNodeWithText(spacedCodeForTest(next)).assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class RecentsPrivacyTest {
    private fun secure(a: ComponentActivity) = a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test @Config(sdk = [30]) fun olderAndroidBlanksPreviewWhilePaused() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val privacy = RecentsPrivacy(activity)
        privacy.setHidden(true)
        assertFalse(secure(activity), "di dalam aplikasi layar tetap bisa di-screenshot")
        privacy.onPause()
        assertTrue(secure(activity))
        privacy.onResume()
        assertFalse(secure(activity))
        privacy.setHidden(false)
        privacy.onPause()
        assertFalse(secure(activity), "tanpa PIN, pratinjau Terkini biasa")
    }

    @Test @Config(sdk = [34]) fun android13UsesRecentsFlagInsteadOfSecureWindow() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val privacy = RecentsPrivacy(activity)
        privacy.setHidden(true)
        privacy.onPause()
        assertFalse(secure(activity))
        privacy.onResume()
    }
}
