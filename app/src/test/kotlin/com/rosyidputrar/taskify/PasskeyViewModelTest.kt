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

/** Alur ViewModel passkey dengan server & Credential Manager palsu (tanpa compose rule → dispatcher Main asli). */
@RunWith(AndroidJUnit4::class)
class PasskeyViewModelTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private class FakeServer : PasswordAuth, PasskeyAuth {
        val calls = mutableListOf<String>()
        var fail: Exception? = null
        val items = mutableListOf(Passkey("p1", "Pixel 8", 1_795_000_000_000, null))
        override suspend fun signIn(email: String, password: String) { calls += "password" }
        override suspend fun passkeySignInChallenge(): PasskeyChallenge { fail?.let { throw it }; calls += "auth-options"; return PasskeyChallenge("c1", """{"challenge":"abc"}""") }
        override suspend fun passkeySignIn(challengeId: String, credentialJson: String) { calls += "auth-verify:$challengeId:$credentialJson" }
        override suspend fun passkeyRegistrationChallenge(): PasskeyChallenge { fail?.let { throw it }; calls += "reg-options"; return PasskeyChallenge("r1", """{"rp":{}}""") }
        override suspend fun passkeyRegister(challengeId: String, credentialJson: String): Passkey {
            calls += "reg-verify:$challengeId:$credentialJson"
            return Passkey("p2", "", 1_795_100_000_000, null).also { items += it }
        }
        override suspend fun passkeys(): List<Passkey> = items.toList()
        override suspend fun renamePasskey(id: String, name: String): Passkey {
            calls += "rename:$id:$name"
            val i = items.indexOfFirst { it.id == id }
            items[i] = items[i].copy(name = name)
            return items[i]
        }
        override suspend fun deletePasskey(id: String) { calls += "delete:$id"; items.removeAll { it.id == id } }
    }

    private class FakeDevice(var problem: PasskeyProblem? = null) : PasskeyDevice {
        val seen = mutableListOf<String>()
        override suspend fun create(optionsJson: String): String { problem?.let { throw it }; seen += "create:$optionsJson"; return "REG" }
        override suspend fun get(optionsJson: String): String { problem?.let { throw it }; seen += "get:$optionsJson"; return "ASSERT" }
    }

    private fun loginVm(server: FakeServer) =
        LoginViewModel(server, LoginNameStore(DevicePrefs(ApplicationProvider.getApplicationContext())))

    @Test fun passkeySignInSendsDeviceAnswerToServer() {
        val server = FakeServer()
        val device = FakeDevice()
        val vm = loginVm(server)
        vm.signInWithPasskey(device)
        idle()
        assertEquals(listOf("""get:{"challenge":"abc"}"""), device.seen, "opsi server diteruskan apa adanya")
        assertEquals(listOf("auth-options", "auth-verify:c1:ASSERT"), server.calls)
        assertEquals(LoginUiState(), vm.state.value, "selesai: tidak memuat & tanpa galat")
    }

    @Test fun cancelledSheetIsSilentAndOtherProblemsExplain() {
        val server = FakeServer()
        val vm = loginVm(server)
        vm.signInWithPasskey(FakeDevice(PasskeyProblem.Cancelled("dibatalkan")))
        idle()
        assertEquals(LoginUiState(), vm.state.value)

        vm.signInWithPasskey(FakeDevice(PasskeyProblem.NoPasskey("NoCredentialException")))
        idle()
        assertTrue(vm.state.value.error!!.startsWith("Belum ada passkey Taskify"), vm.state.value.error)
        assertFalse(vm.state.value.passkeyLoading)

        server.fail = AuthError(AuthFailure(false, 404, null, "Not Found"))
        vm.signInWithPasskey(FakeDevice())
        idle()
        assertTrue(vm.state.value.error!!.startsWith("Passkey belum diaktifkan di server"), vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test fun addRenameAndDeletePasskeys() {
        val server = FakeServer()
        val vm = PasskeysViewModel(server)
        vm.load(); idle()
        assertEquals(listOf("Pixel 8"), vm.state.value.items.map { it.name })

        vm.add(FakeDevice(), deviceName = "Galaxy S25")
        idle()
        assertTrue("reg-verify:r1:REG" in server.calls)
        assertTrue("rename:p2:Galaxy S25" in server.calls, "passkey tanpa nama diberi nama perangkat")
        assertEquals(listOf("Pixel 8", "Galaxy S25"), vm.state.value.items.map { it.name })
        assertEquals("Passkey ditambahkan. Lain kali bisa masuk tanpa password.", vm.state.value.message)

        vm.rename("p1", "  ")
        assertEquals("Nama passkey tidak boleh kosong.", vm.state.value.error)
        vm.rename("p1", "HP lama"); idle()
        vm.delete("p2"); idle()
        assertEquals(listOf("HP lama"), vm.state.value.items.map { it.name })
        assertNull(vm.state.value.error)
    }

    @Test fun addProblemShowsMessageAndStopsBusy() {
        val vm = PasskeysViewModel(FakeServer())
        vm.add(FakeDevice(PasskeyProblem.NotLinked("SecurityError: RP ID cannot be validated")), "HP")
        idle()
        assertTrue("assetlinks.json" in vm.state.value.error!!, vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

}
