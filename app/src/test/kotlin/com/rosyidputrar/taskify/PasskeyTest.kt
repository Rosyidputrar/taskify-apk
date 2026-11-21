package com.rosyidputrar.taskify

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.LoginNameStore
import com.rosyidputrar.taskify.core.auth.Passkey
import com.rosyidputrar.taskify.core.auth.PasskeyAuth
import com.rosyidputrar.taskify.core.auth.PasskeyChallenge
import com.rosyidputrar.taskify.core.auth.PasswordAuth
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.feature.auth.LoginContent
import com.rosyidputrar.taskify.feature.auth.LoginUiState
import com.rosyidputrar.taskify.feature.auth.LoginViewModel
import com.rosyidputrar.taskify.feature.auth.PasskeyDevice
import com.rosyidputrar.taskify.feature.auth.PasskeyProblem
import com.rosyidputrar.taskify.feature.auth.PasskeySettingsContent
import com.rosyidputrar.taskify.feature.auth.PasskeysUi
import com.rosyidputrar.taskify.feature.auth.PasskeysViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Passkey: masuk tanpa password, kelola passkey di Pengaturan, dan galat yang jelas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class PasskeyTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun loginWithPasskeyButton() {
        var clicks = 0
        shot("auth_login_passkey_light") {
            LoginContent("Selamat datang kembali, Rosyid", LoginUiState(), onSubmit = { _, _ -> }, onPasskey = { clicks++ })
        }
        compose.onNodeWithText("Masuk dengan passkey").performClick()
        assertEquals(1, clicks)
    }

    @Test fun passkeySettingsScreenshot() {
        val state = PasskeysUi(
            items = listOf(Passkey("p1", "Pixel 8", 1_795_000_000_000, 1_795_600_000_000), Passkey("p2", "Galaxy Tab", 1_795_100_000_000, null)),
            loading = false,
        )
        shot("settings_passkeys_dark", dark = true) {
            PasskeySettingsContent(state, canAdd = true, origin = "android:apk-key-hash:AbCdEf0123456789", onAdd = {}, onRename = { _, _ -> }, onDelete = {})
        }
        compose.onNodeWithText("Tambah passkey").assertExists()
        compose.onNodeWithText("Info server").performClick()
        compose.onNodeWithText("android:apk-key-hash:AbCdEf0123456789").assertExists()
    }
}
