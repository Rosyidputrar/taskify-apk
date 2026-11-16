package com.rosyidputrar.taskify

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import org.koin.android.ext.android.inject
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.feature.auth.LockScreen
import com.rosyidputrar.taskify.feature.auth.LoginScreen
import com.rosyidputrar.taskify.feature.auth.MfaScreen
import com.rosyidputrar.taskify.ui.gate.ConfigMissingScreen
import com.rosyidputrar.taskify.ui.gate.GateUi
import com.rosyidputrar.taskify.ui.gate.GateViewModel
import com.rosyidputrar.taskify.ui.gate.PreparingScreen
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import com.rosyidputrar.taskify.ui.shell.AppViewModel
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModel()
    private val gateViewModel: GateViewModel by viewModel()
    private val messenger: Messenger by inject()
    private val shareInbox: com.rosyidputrar.taskify.ui.share.ShareInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Splash tetap tampil sampai tema & status login terbaca → tidak ada kedipan layar masuk.
        splash.setKeepOnScreenCondition {
            appViewModel.state.value == null || gateViewModel.ui.value == GateUi.Loading
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val prefs by appViewModel.state.collectAsStateWithLifecycle()
            val gate by gateViewModel.ui.collectAsStateWithLifecycle()
            val snapshot = prefs ?: return@setContent
            TaskifyTheme(darkTheme = snapshot.themeMode.isDark()) {
                AppGate(gate, snapshot)
            }
        }
    }

    @Composable
    private fun AppGate(gate: GateUi, snapshot: AppPreferences.Snapshot) {
        var preview by rememberSaveable { mutableStateOf(false) }
        val shell: @Composable (String?) -> Unit = { name ->
            TaskifyApp(
                preferences = snapshot,
                displayName = name,
                messages = messenger.messages,
                onThemeModeChange = appViewModel::setThemeMode,
                onPinnedModuleChange = appViewModel::setPinnedModule,
            )
        }
        val key = if (gate is GateUi.Open) "open" else gate::class.simpleName
        AnimatedContent(targetState = key, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "gate") { _ ->
            when (gate) {
                GateUi.Loading -> Unit
                GateUi.ConfigMissing -> if (preview) shell(null) else ConfigMissingScreen(onPreview = { preview = true })
                GateUi.SignedOut -> LoginScreen()
                is GateUi.Mfa -> MfaScreen(gate.status)
                GateUi.Preparing -> PreparingScreen()
                GateUi.Locked -> LockScreen()
                is GateUi.Open -> shell(gate.displayName)
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
            ACTION_CALENDAR -> AppEvents.send(AppEvent.OpenCalendar())
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                // Salin isi selagi izin baca dari aplikasi pengirim masih berlaku, lalu buka layar Bagikan.
                lifecycleScope.launch { if (shareInbox.receive(intent)) AppEvents.send(AppEvent.OpenShare) }
            }
            ReminderNotifier.ACTION_OPEN_TASK -> intent.getStringExtra(ReminderNotifier.EXTRA_TASK_ID)?.let { AppEvents.send(AppEvent.OpenTask(it)) }
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
