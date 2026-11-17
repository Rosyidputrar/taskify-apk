package com.rosyidputrar.taskify.ui.gate

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/** Kunci state shell di [androidx.compose.runtime.saveable.SaveableStateHolder]. */
internal const val SHELL_STATE_KEY = "shell"

/**
 * Menampilkan gerbang (masuk, MFA, menyiapkan data, kunci PIN) atau shell aplikasi.
 *
 * Saat terkunci, shell dilepas dari komposisi — sengaja bukan overlay, karena dialog & bottom
 * sheet adalah window terpisah yang akan tampil DI ATAS layar kunci. Supaya pengguna tidak
 * kehilangan pekerjaannya, state shell (back stack navigasi, isian `rememberSaveable`, kunci
 * launcher pemilih berkas/kamera) disimpan di [rememberSaveableStateHolder] dan dipulihkan saat
 * shell tampil lagi. ViewModel per layar tetap hidup di ViewModelStore milik Activity.
 *
 * State dibuang saat keluar (ganti akun selalu melewati layar Masuk), sehingga akun berikutnya
 * tidak mewarisi navigasi akun sebelumnya. Tahap "menyiapkan data" saat aplikasi dibuka ulang
 * setelah proses dimatikan sistem TIDAK membuangnya, jadi pengguna kembali ke layar terakhir.
 */
@Composable
fun GateHost(
    gate: GateUi,
    gateScreen: @Composable (GateUi) -> Unit,
    shell: @Composable (displayName: String?) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    if (gate == GateUi.SignedOut) {
        LaunchedEffect(gate) { holder.removeState(SHELL_STATE_KEY) }
    }
    AnimatedContent(
        targetState = gate,
        contentKey = { if (it is GateUi.Open) "open" else it::class.simpleName },
        // Kunci ↔ aplikasi: "fade through" supaya layar kunci dan isi tidak tampil bertumpuk.
        transitionSpec = { TaskifyMotion.fadeThroughIn() togetherWith TaskifyMotion.fadeThroughOut() },
        label = "gate",
    ) { target ->
        if (target is GateUi.Open) {
            // Nama tampilan terbaru (berubah tanpa mengganti konten).
            val name = (gate as? GateUi.Open)?.displayName ?: target.displayName
            holder.SaveableStateProvider(SHELL_STATE_KEY) { shell(name) }
        } else {
            gateScreen(target)
        }
    }
}
