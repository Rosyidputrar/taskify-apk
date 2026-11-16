package com.rosyidputrar.taskify

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.autofill.AutofillActions
import com.rosyidputrar.taskify.autofill.AutofillContent
import com.rosyidputrar.taskify.autofill.AutofillMode
import com.rosyidputrar.taskify.autofill.AutofillUiState
import com.rosyidputrar.taskify.autofill.AutofillVault
import com.rosyidputrar.taskify.autofill.PendingSave
import com.rosyidputrar.taskify.autofill.VaultPhase
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.AutofillMatch
import com.rosyidputrar.taskify.core.domain.sandi.AutofillTarget
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiPrefs
import com.rosyidputrar.taskify.core.sandi.SandiSession
import com.rosyidputrar.taskify.core.sandi.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** I-12: Sandi sebagai layanan Isi Otomatis. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiAutofillTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun item(id: String, title: String, url: String, user: String = "rosyid@mail.com", fav: Boolean = false) =
        SandiItem(id, ItemPayload(title = title, url = url, username = user, password = "rahasia-$id", favorite = fav), "x", "x")

    private val items = listOf(
        item("1", "Google", "https://accounts.google.com", fav = true),
        item("2", "Gmail kantor", "google.com", user = "rosyid@kantor.id"),
        item("3", "Tokopedia", "https://www.tokopedia.com"),
        item("4", "BCA mobile", "androidapp://com.bca", user = "rosyid88"),
    )
    private val target = AutofillTarget("com.android.chrome", "accounts.google.com")

    private fun state(phase: VaultPhase, mode: AutofillMode = AutofillMode.Fill, pending: PendingSave? = null) = AutofillUiState(
        mode = mode,
        target = pending?.target ?: target,
        phase = phase,
        canUseDevice = true,
        items = items,
        matches = AutofillMatch.rank(items, pending?.target ?: target) { it.payload },
        pending = pending,
    )

    @Test fun lockedScreenshot() {
        compose.setContent { TaskifyTheme(darkTheme = true) { AutofillContent(state(VaultPhase.Locked), object : AutofillActions {}) } }
        compose.onNodeWithText("Isi untuk accounts.google.com").assertExists()
        compose.onNodeWithText("Pakai sidik jari / kunci layar").assertExists()
        compose.onNodeWithText("Buka brankas").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("screenshots/autofill_locked_dark.png")
    }

    @Test fun pickMatchesFirstAndConfirmsMismatch() {
        val picked = mutableStateListOf<String>()
        val actions = object : AutofillActions {
            override fun pick(item: SandiItem) { picked += item.id }
        }
        compose.setContent { TaskifyTheme { AutofillContent(state(VaultPhase.Unlocked), actions) } }
        compose.onNodeWithText("COCOK UNTUK ACCOUNTS.GOOGLE.COM").assertExists()
        compose.onRoot().captureRoboImage("screenshots/autofill_pick_light.png")
        compose.onNodeWithText("Google").performClick()
        assertEquals(listOf("1"), picked.toList())
        // Item untuk situs lain: harus dikonfirmasi dulu (anti-phishing).
        compose.onNodeWithText("Tokopedia").performClick()
        compose.onNodeWithText("Alamat tidak cocok").assertExists()
        assertEquals(listOf("1"), picked.toList())
        compose.onNodeWithText("Tetap isi").performClick()
        assertEquals(listOf("1", "3"), picked.toList())
    }

    @Test fun saveScreenshotShowsUpdateTarget() {
        val pending = PendingSave(AutofillTarget("com.bca"), "rosyid88", "sandi-baru")
        val st = state(VaultPhase.Unlocked, AutofillMode.Save, pending).let { s -> s.copy(saveTarget = s.matches.first()) }
        compose.setContent { TaskifyTheme { AutofillContent(st, object : AutofillActions {}) } }
        compose.onNodeWithText("Sandi untuk “BCA mobile” akan diperbarui.").assertExists()
        compose.onNodeWithText("Perbarui").assertExists()
        compose.onRoot().captureRoboImage("screenshots/autofill_save_light.png")
    }

    @Test fun savePlanUpdatesOnlyMatchingUsername() {
        val bca = AutofillTarget("com.bca")
        assertEquals(AutofillVault.SavePlan.Unchanged, AutofillVault.planSave(items, bca, "rosyid88", "rahasia-4"))
        val update = AutofillVault.planSave(items, bca, "rosyid88", "baru") as AutofillVault.SavePlan.Update
        assertEquals("4", update.id)
        assertEquals("baru", update.payload.password)
        val create = AutofillVault.planSave(items, bca, "akun-lain", "x") as AutofillVault.SavePlan.Create
        assertEquals(ItemPayload(title = "com.bca", url = "androidapp://com.bca", username = "akun-lain", password = "x"), create.payload)
        // Domain lain dengan username sama → item baru, bukan menimpa milik situs lain.
        val other = AutofillVault.planSave(items, AutofillTarget("com.android.chrome", "evil.example"), "rosyid@mail.com", "x")
        assertTrue(other is AutofillVault.SavePlan.Create)
    }

    @Test fun vaultPastAutoLockIsNotUsedForAutofill() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val prefs = SandiPrefs(app).apply { autoLockMinutes = 5 }
        val session = SandiSession(prefs, SecureClipboard(app, scope), scope)
        session.unlock(Dek(ByteArray(32) { 1 }), "stamp")
        session.onStop(ProcessLifecycleOwner.get()) // Taskify ke latar
        val now = System.currentTimeMillis()
        assertNotNull(session.openForAutofill(now + 60_000))
        assertNull(session.openForAutofill(now + 6 * 60_000))
        assertNull(session.vault.value, "lewat batas → brankas dikunci, bukan dipakai")
    }
}
