package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import com.rosyidputrar.taskify.feature.files.PublicFileScreen

/**
 * Tujuan App Link `https://<PUBLIC_WEB_ORIGIN>/unduh/<token>`. Activity TERPISAH dari gerbang
 * login/PIN: penerima tautan tidak boleh mendarat di layar masuk aplikasi pribadi, dan halaman ini
 * tidak memakai sesi siapa pun (sama dengan halaman publik webapp).
 */
class PublicFileActivity : ComponentActivity() {
    private var token by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        token = tokenOf(intent)
        setContent {
            // Tema yang dipilih pengguna juga berlaku di halaman unduhan publik.
            val prefs = remember { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.rosyidputrar.taskify.data.AppPreferences>() }
            val snapshot by (prefs?.snapshot ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
            val dark = snapshot?.themeMode?.isDark() ?: isSystemInDarkTheme()
            TaskifyTheme(darkTheme = dark, spec = snapshot?.theme ?: com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec.Default) { PublicFileScreen(token) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        token = tokenOf(intent)
    }

    companion object {
        /** Tautan terpotong/rusak → null → "Berkas tidak tersedia" (tanpa bertanya ke server). */
        fun tokenOf(intent: Intent?): String? = intent?.data?.let { uri: Uri -> uri.path?.let(TempFiles::shareKeyFromPath) }
    }
}
