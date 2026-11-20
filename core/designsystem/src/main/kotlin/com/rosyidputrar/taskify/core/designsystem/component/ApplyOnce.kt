package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Jalankan [block] SEKALI per entri layar (bertahan saat rotasi & saat kembali dari layar lain),
 * mis. menerapkan tab/filter awal dari argumen rute. Tanpa ini, kembali dari detail ke layar yang
 * sama akan menimpa tab yang sudah dipilih pengguna dengan tab awal lagi.
 */
@Composable
fun ApplyOnce(key: Any?, block: () -> Unit) {
    var done by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!done) {
            done = true
            if (key != null) block()
        }
    }
}
