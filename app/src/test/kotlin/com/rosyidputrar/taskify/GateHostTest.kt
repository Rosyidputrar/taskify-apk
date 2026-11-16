package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.ui.gate.GateHost
import com.rosyidputrar.taskify.ui.gate.GateUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kunci PIN / konfirmasi ulang MFA di tengah pemakaian tidak boleh membuang navigasi & isian
 * pengguna; keluar akun harus membuangnya.
 */
@RunWith(AndroidJUnit4::class)
class GateHostTest {
    @get:Rule val compose = createComposeRule()

    private var gate by mutableStateOf<GateUi>(GateUi.Open("Rosyid"))

    private fun setHost() = compose.setContent {
        GateHost(
            gate = gate,
            gateScreen = { Text("Gerbang: ${it::class.simpleName}") },
        ) { name ->
            val nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") {
                    Column {
                        Text("Beranda $name")
                        Button(onClick = { nav.navigate("editor") }) { Text("Buka editor") }
                    }
                }
                composable("editor") {
                    var draft by rememberSaveable { mutableStateOf("") }
                    TextField(draft, { draft = it }, Modifier.testTag("draft"))
                }
            }
        }
    }

    private fun openEditorAndType(text: String) {
        compose.onNodeWithText("Buka editor").performClick()
        compose.onNodeWithTag("draft").performTextInput(text)
        compose.waitForIdle()
    }

    @Test fun lockAndUnlockKeepsNavigationAndDraft() {
        setHost()
        openEditorAndType("catatan belum disimpan")

        gate = GateUi.Locked
        compose.waitForIdle()
        compose.onNodeWithText("Gerbang: Locked").assertIsDisplayed()
        compose.onNodeWithTag("draft").assertDoesNotExist()

        gate = GateUi.Open("Rosyid")
        compose.waitForIdle()
        compose.onNodeWithTag("draft").assertTextContains("catatan belum disimpan")
    }

    @Test fun mfaReverifyKeepsState() {
        setHost()
        openEditorAndType("draf")
        gate = GateUi.Mfa(GateStatus.Reverify)
        compose.waitForIdle()
        gate = GateUi.Open("Rosyid")
        compose.waitForIdle()
        compose.onNodeWithTag("draft").assertTextContains("draf")
    }

    @Test fun signOutDiscardsState() {
        setHost()
        openEditorAndType("milik akun lama")

        gate = GateUi.SignedOut
        compose.waitForIdle()
        gate = GateUi.Preparing
        compose.waitForIdle()
        gate = GateUi.Open("Akun baru")
        compose.waitForIdle()

        compose.onNodeWithText("Beranda Akun baru").assertIsDisplayed()
        compose.onNodeWithTag("draft").assertDoesNotExist()
    }

    @Test fun displayNameUpdatesWithoutLosingState() {
        setHost()
        openEditorAndType("x")
        gate = GateUi.Open("Nama baru")
        compose.waitForIdle()
        compose.onNodeWithTag("draft").assertTextContains("x")
    }
}
