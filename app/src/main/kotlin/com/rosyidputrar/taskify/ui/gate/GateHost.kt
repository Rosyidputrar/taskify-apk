package com.rosyidputrar.taskify.ui.gate

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import com.rosyidputrar.taskify.ui.shell.AppEvents
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** Kunci state shell di [androidx.compose.runtime.saveable.SaveableStateHolder]. */
internal const val SHELL_STATE_KEY = "shell"

/** Kunci stabil (tahan obfuscation R8) untuk setiap status gerbang, termasuk perbedaan sub-status MFA. */
internal fun gateContentKey(gate: GateUi): String = when (gate) {
    GateUi.Loading -> "loading"
    GateUi.ConfigMissing -> "config-missing"
    GateUi.SignedOut -> "signed-out"
    is GateUi.Mfa -> "mfa-${gate.status.name}"
    GateUi.Preparing -> "preparing"
    GateUi.Locked -> "locked"
    is GateUi.Open -> "open"
}

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * Menampilkan gerbang (masuk, MFA, menyiapkan data, kunci PIN) atau shell aplikasi.
 *
 * Saat terkunci, shell dilepas dari komposisi — sengaja bukan overlay, karena dialog & bottom
 * sheet adalah window terpisah yang akan tampil DI ATAS layar kunci. Supaya pengguna tidak
 * kehilangan pekerjaannya, state shell (back stack navigasi, isian `rememberSaveable`, kunci
 * launcher pemilih berkas/kamera) disimpan di [rememberSaveableStateHolder] dan dipulihkan saat
 * shell tampil lagi.
 *
 * Tiap layar gerbang (Login, MFA, Lock) diberi [ViewModelStoreOwner] sendiri yang dibersihkan
 * saat layar gerbang ditinggalkan — mencegah `LockViewModel` menyimpan panjang PIN lama dan
 * mencegah `MfaViewModel` membocorkan faktor/secret TOTP ke sesi/akun berikutnya.
 *
 * State dibuang saat keluar (ganti akun selalu melewati layar Masuk), sehingga akun berikutnya
 * tidak mewarisi navigasi atau antrean event akun sebelumnya.
 */
@Composable
fun GateHost(
    gate: GateUi,
    gateScreen: @Composable (GateUi) -> Unit,
    shell: @Composable (displayName: String?) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    if (gate == GateUi.SignedOut) {
        LaunchedEffect(gate) {
            holder.removeState(SHELL_STATE_KEY)
            AppEvents.clear()
        }
    }
    AnimatedContent(
        targetState = gate,
        contentKey = ::gateContentKey,
        // Kunci ↔ aplikasi: "fade through" supaya layar kunci dan isi tidak tampil bertumpuk.
        transitionSpec = { TaskifyMotion.fadeThroughIn() togetherWith TaskifyMotion.fadeThroughOut() },
        label = "gate",
    ) { target ->
        if (target is GateUi.Open) {
            // Nama tampilan terbaru (berubah tanpa mengganti konten).
            val name = (gate as? GateUi.Open)?.displayName ?: target.displayName
            holder.SaveableStateProvider(SHELL_STATE_KEY) { shell(name) }
        } else {
            val key = gateContentKey(target)
            val owner = remember(key) { ScopedViewModelStoreOwner() }
            DisposableEffect(owner) {
                onDispose { owner.viewModelStore.clear() }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                gateScreen(target)
            }
        }
    }
}
