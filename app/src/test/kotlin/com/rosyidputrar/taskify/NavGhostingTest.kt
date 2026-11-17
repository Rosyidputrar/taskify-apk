package com.rosyidputrar.taskify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.ui.shell.Screen
import com.rosyidputrar.taskify.ui.shell.enter
import com.rosyidputrar.taskify.ui.shell.exit
import com.rosyidputrar.taskify.ui.shell.popEnter
import com.rosyidputrar.taskify.ui.shell.popExit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Regresi "back tembus pandang" (Keuangan terlihat di balik Ruang): selama transisi push/pop,
 * layar teratas harus buram penuh — piksel di area yang masih ditutupinya tidak boleh tercampur
 * warna layar di bawahnya.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class NavGhostingTest {
    @get:Rule val compose = createComposeRule()
    private val nav = mutableStateOf<NavHostController?>(null)
    private var background = Color.Unspecified

    private fun host() {
        compose.setContent {
            TaskifyTheme {
                background = MaterialTheme.colorScheme.background
                val controller = rememberNavController().also { nav.value = it }
                NavHost(
                    controller, "ruang", Modifier.fillMaxSize().testTag("host"),
                    enterTransition = { enter(null) }, exitTransition = { exit(null) },
                    popEnterTransition = { popEnter(null) }, popExitTransition = { popExit(null) },
                ) {
                    // Layar "bawah" berwarna merah penuh supaya tembusan sekecil apa pun terdeteksi.
                    composable("ruang") { Screen { Box(Modifier.fillMaxSize().background(Color.Red)) } }
                    // Layar "atas" seperti layar biasa: hanya teks, tanpa latar sendiri.
                    composable("keuangan") { Screen { Text("Keuangan") } }
                }
            }
        }
    }

    private fun pixelAt(fx: Float, fy: Float): Color {
        val img = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        return img[(img.width * fx).toInt(), (img.height * fy).toInt()]
    }

    private fun assertClean(c: Color, what: String) {
        val bg = background
        val close = abs(c.red - bg.red) < 0.02f && abs(c.green - bg.green) < 0.02f && abs(c.blue - bg.blue) < 0.02f
        assertTrue(close, "$what: piksel ${Integer.toHexString(c.toArgb())} tercampur layar di bawahnya (latar ${Integer.toHexString(bg.toArgb())})")
    }

    @Test fun backDoesNotGhost() {
        host()
        compose.runOnIdle { nav.value!!.navigate("keuangan") }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.value!!.popBackStack() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        // Keuangan baru sedikit bergeser ke kanan: sisi kanan masih tertutup Keuangan sepenuhnya.
        assertClean(pixelAt(0.9f, 0.6f), "kembali")
        compose.mainClock.autoAdvance = true
    }

    @Test fun pushDoesNotGhost() {
        host()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.value!!.navigate("keuangan") }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(250)
        // Keuangan sudah menutupi sebagian besar layar dari kanan.
        assertClean(pixelAt(0.95f, 0.6f), "maju")
        compose.mainClock.autoAdvance = true
    }
}
