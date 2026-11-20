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
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val nav = mutableStateOf<NavHostController?>(null)
    private var background = Color.Unspecified

    private var topColor = Color.Unspecified

    private fun host(topFill: Color? = null) {
        topColor = topFill ?: Color.Unspecified
        compose.setContent {
            TaskifyTheme {
                background = MaterialTheme.colorScheme.background
                val controller = rememberNavController().also { nav.value = it }
                NavHost(
                    controller, "ruang", Modifier.fillMaxSize().testTag("host"),
                    enterTransition = { enter(null) }, exitTransition = { exit(null) },
                    popEnterTransition = { popEnter(null) }, popExitTransition = { popExit(null) },
                    predictivePopEnterTransition = { popEnter(null) }, predictivePopExitTransition = { popExit(null) },
                ) {
                    // Layar "bawah" berwarna merah penuh supaya tembusan sekecil apa pun terdeteksi.
                    composable("ruang") { Screen { Box(Modifier.fillMaxSize().background(Color.Red)) } }
                    // Layar "atas" seperti layar biasa: hanya teks, tanpa latar sendiri.
                    composable("keuangan") { Screen { if (topFill != null) Box(Modifier.fillMaxSize().background(topFill)) else Text("Keuangan") } }
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

    /** Back ditahan (predictive back): layar yang ditutup bergeser ke kanan seperti Animeru, bukan mengecil. */
    @Test fun predictiveBackSlidesInsteadOfScaling() {
        host(topFill = Color.Blue)
        compose.runOnIdle { nav.value!!.navigate("keuangan") }
        compose.waitForIdle()
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f, 400f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        // Geser: tepi kanan masih tertutup Keuangan (biru), sisi kiri sudah menampakkan Ruang.
        // Bawaan library (mengecil ke 70%) akan memperlihatkan Ruang (merah) di tepi kanan.
        val right = pixelAt(0.97f, 0.5f)
        assertTrue(right.blue > 0.8f && right.red < 0.2f, "tepi kanan saat back ditahan: ${Integer.toHexString(right.toArgb())} (harus layar Keuangan)")
        val left = pixelAt(0.03f, 0.5f)
        assertTrue(left.blue < 0.5f, "sisi kiri saat back ditahan harus sudah menampakkan Ruang: ${Integer.toHexString(left.toArgb())}")
        compose.runOnIdle { back.dispatchOnBackCancelled() }
    }

    /**
     * Tab dok (Hari Ini → Ruang) dan editor layar penuh memakai "fade through": di frame mana pun,
     * isi dua layar tidak boleh tampil bersamaan (dulu keduanya memudar bersamaan → bertumpuk).
     */
    private fun assertNeverStacked(go: (NavHostController) -> Unit) {
        val bottom = Color(1f, 0f, 0f)
        val top = Color(0f, 1f, 0f)
        compose.setContent {
            TaskifyTheme {
                background = MaterialTheme.colorScheme.background
                val controller = rememberNavController().also { nav.value = it }
                NavHost(
                    controller, com.rosyidputrar.taskify.navigation.HomeRoute, Modifier.fillMaxSize().testTag("host"),
                    enterTransition = { enter(null) }, exitTransition = { exit(null) },
                    popEnterTransition = { popEnter(null) }, popExitTransition = { popExit(null) },
                    predictivePopEnterTransition = { popEnter(null) }, predictivePopExitTransition = { popExit(null) },
                ) {
                    composable<com.rosyidputrar.taskify.navigation.HomeRoute> { Screen { Box(Modifier.fillMaxSize().background(bottom)) } }
                    composable<com.rosyidputrar.taskify.navigation.RoomRoute> { Screen { Box(Modifier.fillMaxSize().background(top)) } }
                    composable<com.rosyidputrar.taskify.navigation.NoteEditRoute> { Screen { Box(Modifier.fillMaxSize().background(top)) } }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { go(nav.value!!) }
        val bg = background
        var sawTop = false
        repeat(24) { frame ->
            compose.mainClock.advanceTimeBy(16)
            val c = pixelAt(0.5f, 0.5f)
            // c = a·merah + b·hijau + k·latar → k dari kanal biru (merah & hijau tak punya biru).
            val k = (c.blue / bg.blue).coerceIn(0f, 1f)
            val a = (c.red - k * bg.red).coerceAtLeast(0f)
            val b = (c.green - k * bg.green).coerceAtLeast(0f)
            if (b > 0.5f) sawTop = true
            assertTrue(minOf(a, b) < 0.06f, "frame ${frame * 16} ms: dua layar bertumpuk (merah ${"%.2f".format(a)}, hijau ${"%.2f".format(b)})")
        }
        compose.mainClock.autoAdvance = true
        assertTrue(sawTop, "layar tujuan harus sudah tampil")
    }

    @Test fun dockTabSwitchNeverStacksScreens() = assertNeverStacked { it.navigate(com.rosyidputrar.taskify.navigation.RoomRoute) }

    @Test fun fullScreenEditorNeverStacksScreens() = assertNeverStacked { it.navigate(com.rosyidputrar.taskify.navigation.NoteEditRoute()) }

    /**
     * A12 — back ganda cepat dari tiga lapis: dua pop harus mendarat tepat di layar awal tanpa
     * crash dan tanpa melewatkannya (back kedua tidak boleh "menembus" layar awal ke luar aplikasi).
     */
    @Test fun doubleBackLandsOnStartWithoutCrash() {
        host()
        compose.runOnIdle { nav.value!!.navigate("keuangan") }
        compose.waitForIdle()
        compose.runOnIdle { nav.value!!.navigate("keuangan") }
        compose.waitForIdle()
        val back = compose.activity.onBackPressedDispatcher
        // Dua back tanpa jeda (tanpa waitForIdle di antaranya), seperti ketukan ganda.
        compose.runOnIdle { back.onBackPressed(); back.onBackPressed() }
        compose.waitForIdle()
        assertTrue(nav.value!!.currentDestination?.route == "ruang", "back ganda harus berhenti di Ruang")
        assertTrue(!compose.activity.isFinishing, "back ganda tidak boleh menutup aplikasi dari Ruang")
    }

    /** Host rute bertipe untuk menguji aturan transisi (enter/exit memakai [pinned] yang sama). */
    private fun typedHost(pinned: com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule?) {
        val bottom = Color(1f, 0f, 0f)
        val top = Color(0f, 1f, 0f)
        compose.setContent {
            TaskifyTheme {
                background = MaterialTheme.colorScheme.background
                val controller = rememberNavController().also { nav.value = it }
                NavHost(
                    controller, com.rosyidputrar.taskify.navigation.HomeRoute, Modifier.fillMaxSize().testTag("host"),
                    enterTransition = { enter(pinned) }, exitTransition = { exit(pinned) },
                    popEnterTransition = { popEnter(pinned) }, popExitTransition = { popExit(pinned) },
                    predictivePopEnterTransition = { popEnter(pinned) }, predictivePopExitTransition = { popExit(pinned) },
                ) {
                    composable<com.rosyidputrar.taskify.navigation.HomeRoute> { Screen { Box(Modifier.fillMaxSize().background(bottom)) } }
                    composable<com.rosyidputrar.taskify.navigation.RoomRoute> { Screen { Box(Modifier.fillMaxSize().background(top)) } }
                    composable<com.rosyidputrar.taskify.navigation.TasksRoute> { Screen { Box(Modifier.fillMaxSize().background(top)) } }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Koefisien merah/hijau piksel (pola yang sama dengan assertNeverStacked). */
    private fun coefficients(c: Color): Pair<Float, Float> {
        val bg = background
        val k = (c.blue / bg.blue).coerceIn(0f, 1f)
        val a = (c.red - k * bg.red).coerceAtLeast(0f)
        val b = (c.green - k * bg.green).coerceAtLeast(0f)
        return a to b
    }

    /**
     * A12 — detail Tugas saat Tugas disematkan harus GESER (push), bukan fade-through:
     * di tengah transisi, layar lama masih terlihat di kiri sementara layar baru sudah masuk
     * dari kanan. Fade-through tidak pernah menunjukkan keduanya sekaligus.
     */
    @Test fun pinnedTaskDetailSlidesInsteadOfFadingThrough() {
        typedHost(com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Tasks)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.value!!.navigate(com.rosyidputrar.taskify.navigation.TasksRoute("t1")) }
        var sawBothSides = false
        repeat(24) {
            compose.mainClock.advanceTimeBy(16)
            val (leftRed, _) = coefficients(pixelAt(0.1f, 0.5f))
            val (_, rightGreen) = coefficients(pixelAt(0.9f, 0.5f))
            if (leftRed > 0.3f && rightGreen > 0.5f) sawBothSides = true
        }
        compose.mainClock.autoAdvance = true
        assertTrue(sawBothSides, "detail Tugas yang disematkan harus bergeser (kiri = daftar lama, kanan = detail baru), bukan fade-through")
    }

    /** Kontrol pembeda: tab dok (Hari Ini → Ruang) TIDAK PERNAH menunjukkan dua sisi sekaligus. */
    @Test fun dockTabSwitchNeverShowsBothSides() {
        typedHost(com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Tasks)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.value!!.navigate(com.rosyidputrar.taskify.navigation.RoomRoute) }
        var sawBothSides = false
        repeat(24) {
            compose.mainClock.advanceTimeBy(16)
            val (leftRed, _) = coefficients(pixelAt(0.1f, 0.5f))
            val (_, rightGreen) = coefficients(pixelAt(0.9f, 0.5f))
            if (leftRed > 0.3f && rightGreen > 0.5f) sawBothSides = true
        }
        compose.mainClock.autoAdvance = true
        assertTrue(!sawBothSides, "fade-through tab dok tidak boleh menampilkan daftar lama dan layar baru sekaligus")
    }
}
