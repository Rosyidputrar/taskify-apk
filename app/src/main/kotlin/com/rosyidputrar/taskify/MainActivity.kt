package com.rosyidputrar.taskify

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import com.rosyidputrar.taskify.ui.shell.AppViewModel
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.state.value == null }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val prefs by appViewModel.state.collectAsStateWithLifecycle()
            val snapshot = prefs ?: return@setContent
            TaskifyTheme(darkTheme = snapshot.themeMode.isDark()) {
                TaskifyApp(
                    preferences = snapshot,
                    onThemeModeChange = appViewModel::setThemeMode,
                    onPinnedModuleChange = appViewModel::setPinnedModule,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CAPTURE -> AppEvents.send(AppEvent.OpenCapture())
            ACTION_SEARCH -> AppEvents.send(AppEvent.OpenSpotlight)
            ACTION_CALENDAR -> AppEvents.send(AppEvent.OpenCalendar)
        }
    }

    /** Ctrl/⌘ + K dari keyboard fisik (tablet, Chromebook, DeX) membuka Spotlight di layar mana pun. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_K &&
            (event.isCtrlPressed || event.isMetaPressed)
        ) {
            AppEvents.send(AppEvent.OpenSpotlight)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val ACTION_CAPTURE = "com.rosyidputrar.taskify.action.CAPTURE"
        const val ACTION_SEARCH = "com.rosyidputrar.taskify.action.SEARCH"
        const val ACTION_CALENDAR = "com.rosyidputrar.taskify.action.CALENDAR"
    }
}
