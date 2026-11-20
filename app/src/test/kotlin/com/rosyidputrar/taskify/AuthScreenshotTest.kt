package com.rosyidputrar.taskify

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.auth.TotpEnrollment
import com.rosyidputrar.taskify.core.auth.TotpFactor
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.feature.auth.LockContent
import com.rosyidputrar.taskify.feature.auth.LockUiState
import com.rosyidputrar.taskify.feature.auth.LoginContent
import com.rosyidputrar.taskify.feature.auth.LoginUiState
import com.rosyidputrar.taskify.feature.auth.MfaActions
import com.rosyidputrar.taskify.feature.auth.MfaContent
import com.rosyidputrar.taskify.feature.auth.MfaUiState
import com.rosyidputrar.taskify.ui.gate.ConfigMissingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class AuthScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun login() = shot("auth_login_light") {
        LoginContent("Selamat datang kembali, Rosyid", LoginUiState(error = "Email atau password salah."), onSubmit = { _, _ -> })
    }

    @Test fun mfaChallengeDark() = shot("auth_mfa_challenge_dark", dark = true) {
        MfaContent(
            GateStatus.Challenge,
            MfaUiState(factors = listOf(TotpFactor("a", "Pixel 8"), TotpFactor("b", "Laptop")), selectedFactor = "a"),
            online = true,
            actions = MfaActions(),
        )
    }

    @Test fun mfaEnroll() = shot("auth_mfa_enroll_light") {
        MfaContent(
            GateStatus.Enroll,
            MfaUiState(enrollment = TotpEnrollment("f", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", "otpauth://totp/Taskify:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Taskify")),
            online = true,
            actions = MfaActions(),
        )
    }

    @Test fun reverifyOffline() = shot("auth_mfa_reverify_offline_light") {
        // D-02: tanpa jejak verifikasi dalam 24 jam terakhir, tombol "Lewati" tidak ada —
        // layar terkunci sampai online. Varian inilah yang direkam ulang dengan sengaja.
        MfaContent(GateStatus.Reverify, MfaUiState(), online = false, actions = MfaActions(), canSkipOffline = false)
    }

    @Test fun lockDark() = shot("auth_lock_dark", dark = true) {
        LockContent(6, LockUiState(entered = "12", message = "PIN salah. Sisa 3 percobaan."), {}, {}, {}, {})
    }

    @Test fun configMissing() = shot("gate_config_missing_light") { ConfigMissingScreen(onPreview = {}) }
}
