package com.rosyidputrar.taskify

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.SecureWindow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SecureWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val secure get() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private var first by mutableStateOf(true)
    private var second by mutableStateOf(false)

    @Test fun flagFollowsSecretScreens() {
        compose.setContent {
            if (first) SecureWindow()
            if (second) SecureWindow()
            Text("isi")
        }
        compose.waitForIdle()
        assertTrue(secure, "layar rahasia tampil → FLAG_SECURE")

        // Transisi: layar rahasia kedua masuk sebelum yang pertama keluar.
        second = true
        compose.waitForIdle()
        first = false
        compose.waitForIdle()
        assertTrue(secure, "layar yang keluar tidak boleh mencabut flag milik layar yang masih tampil")

        second = false
        compose.waitForIdle()
        assertFalse(secure, "semua layar rahasia ditutup → flag dicabut")
    }
}
