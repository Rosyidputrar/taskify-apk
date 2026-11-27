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
import com.rosyidputrar.taskify.feature.vault.PublicSnippetScreen

/**
 * Tujuan App Link `https://<PUBLIC_WEB_ORIGIN>/snippet/<slug>`. Activity TERPISAH dari gerbang
 * login/PIN, sama seperti PublicFileActivity: penerima tautan tidak mendarat di layar masuk
 * aplikasi pribadi dan halaman ini tidak memakai sesi siapa pun.
 */
class PublicSnippetActivity : ComponentActivity() {
    private var slug by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        slug = slugOf(intent)
        setContent {
            val prefs = remember { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.rosyidputrar.taskify.data.AppPreferences>() }
            val snapshot by (prefs?.snapshot ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
            val dark = snapshot?.themeMode?.isDark() ?: isSystemInDarkTheme()
            TaskifyTheme(darkTheme = dark, spec = snapshot?.theme ?: com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec.Default) { PublicSnippetScreen(slug) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        slug = slugOf(intent)
    }

    companion object {
        /** Tautan terpotong/rusak → null → "Snippet tidak tersedia" (tanpa bertanya ke server). */
        fun slugOf(intent: Intent?): String? = intent?.data?.let { uri: Uri -> uri.path?.let(TempFiles::snippetSlugFromPath) }
    }
}
