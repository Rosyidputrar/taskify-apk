package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigPayload
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.KeyRecord
import com.rosyidputrar.taskify.core.domain.sandi.Strength
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.domain.sandi.TotpLogEntry
import com.rosyidputrar.taskify.core.domain.sandi.strengthFromBits
import com.rosyidputrar.taskify.core.sandi.DecryptedItems
import com.rosyidputrar.taskify.core.sandi.DecryptedVersion
import com.rosyidputrar.taskify.core.sandi.DecryptedVersions
import com.rosyidputrar.taskify.core.sandi.OpenVault
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.TotpLogItem
import com.rosyidputrar.taskify.core.sandi.VaultStatus
import com.rosyidputrar.taskify.core.store.sandi.ConfigFile
import com.rosyidputrar.taskify.core.store.sandi.ConfigVersionRow
import com.rosyidputrar.taskify.core.store.sandi.KeyRow
import com.rosyidputrar.taskify.feature.sandi.ItemDetail
import com.rosyidputrar.taskify.feature.sandi.RecoveryKeyCard
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SandiTab
import com.rosyidputrar.taskify.feature.sandi.SandiUiState
import com.rosyidputrar.taskify.feature.sandi.SettingsBody
import com.rosyidputrar.taskify.feature.vault.VaultContent
import com.rosyidputrar.taskify.feature.vault.VaultTab
import com.rosyidputrar.taskify.feature.vault.config.ConfigEditorActions
import com.rosyidputrar.taskify.feature.vault.config.ConfigEditorContent
import com.rosyidputrar.taskify.feature.vault.config.ConfigEditorUiState
import com.rosyidputrar.taskify.feature.vault.config.ConfigFileActions
import com.rosyidputrar.taskify.feature.vault.config.ConfigFileContent
import com.rosyidputrar.taskify.feature.vault.config.ConfigFileUiState
import com.rosyidputrar.taskify.ui.placeholder.BackButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

object SampleSandi {
    /** 2026-11-15 10:00:12 WIB — detik ke-12 periode 30 dtk. */
    const val NOW = 1_763_175_612_000L
    private val key = KeyRow("k1", KeyRecord(1, "pbkdf2-sha256", 600_000, "c2FsdA==", "d3JhcA==", "cnNhbHQ=", "cndyYXA="))
    private val open = OpenVault(Dek(ByteArray(32) { it.toByte() }), "d3JhcA==")

    private fun item(id: String, payload: ItemPayload) = SandiItem(id, payload, "2026-11-01T03:00:00.000Z", "2026-11-10T03:00:00.000Z")
    val items = listOf(
        item("i1", ItemPayload("GitHub", "https://github.com/login", "rosyid@example.com", "Kopi-Hitam-Senja-42!", favorite = true, totp = TotpConfig("JBSWY3DPEHPK3PXP"))),
        item("i2", ItemPayload("Bank Jago", "jago.com", "rosyidputra", "x9#Lm2!qPz7vR4", notes = "PIN aplikasi beda dengan sandi web.")),
        item("i3", ItemPayload("Cloudflare", "https://dash.cloudflare.com", "ops@example.com", "", totp = TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", digits = 6))),
        item("i4", ItemPayload("Tokopedia", "https://www.tokopedia.com", "08123456789", "hujan-sore-di-bandung")),
        item("i5", ItemPayload("Wi-Fi Rumah", "", "", "RumahKita2026")),
    )

    fun state(status: String = "unlocked", tab: SandiTab = SandiTab.Vault) = SandiUiState(
        status = when (status) { "none" -> VaultStatus.NoVault; "locked" -> VaultStatus.Locked(key); else -> VaultStatus.Unlocked(key, open) },
        tab = tab,
        items = DecryptedItems(items, emptyList(), true),
        log = listOf(TotpLogItem("l1", "2026-11-15T02:58:00.000Z", TotpLogEntry("i1", "GitHub", "492039", "2026-11-15T02:58:00.000Z"))),
        now = NOW,
        trustDaysLeft = 27,
        autoLock = 5,
        generator = Generator.Prefs(mode = Generator.Mode.Passphrase),
        generated = "lentil-orbit-cactus-dwelling-7-sprout",
        email = "rosyid@example.com",
    )

    object Actions : SandiActions, ConfigFileActions, ConfigEditorActions {
        override fun setTab(tab: SandiTab) = Unit
        override fun setSearch(text: String) = Unit
        override fun clearError() = Unit
        override fun touch() = Unit
        override fun tryTrusted() = Unit
        override fun unlock(password: String, remember: Boolean) = Unit
        override suspend fun evaluateMaster(password: String): Pair<Strength?, String?> = strengthFromBits(password.length * 4.0) to null
        override fun createVault(password: String) = Unit
        override fun recover(recoveryKey: String, newPassword: String) = Unit
        override fun changePassword(old: String, new: String) = Unit
        override fun rotateRecovery(password: String) = Unit
        override fun acknowledgeRecovery() = Unit
        override fun lock() = Unit
        override fun setAutoLock(minutes: Int) = Unit
        override fun forgetDevice() = Unit
        override fun dismissRejectedKey() = Unit
        override fun dismissRejectedRotation() = Unit
        override fun dismissStaleWrites() = Unit
        override fun save(payload: ItemPayload, id: String?, onDone: () -> Unit) = Unit
        override fun remove(item: SandiItem) = Unit
        override fun copy(what: String, text: String, secret: Boolean) = Unit
        override fun copyCode(item: SandiItem, code: String) = Unit
        override fun saveQuickTotp(title: String, config: TotpConfig, username: String, onDone: () -> Unit) = Unit
        override fun deleteLog(id: String) = Unit
        override fun clearLog() = Unit
        override fun setGenerator(p: Generator.Prefs) = Unit
        override fun regenerate() = Unit
        // konfigurasi
        override fun select(versionId: String) = Unit
        override fun copyValue(key: String, value: String) = Unit
        override fun copyRaw(text: String) = Unit
        override fun restore(version: DecryptedVersion) = Unit
        override fun updateMeta(name: String, environment: String) = Unit
        override fun remove(onDone: () -> Unit) = Unit
        override fun setName(v: String) = Unit
        override fun setFormat(v: ConfigFormat) = Unit
        override fun setEnvironment(v: String) = Unit
        override fun setProject(id: String?) = Unit
        override fun setText(v: String) = Unit
        override fun setNote(v: String) = Unit
        override fun save(onSaved: (String) -> Unit) = Unit
    }

    val configFiles = listOf(
        ConfigFile("c1", ".env.production", ConfigFormat.Env, "production", "p1", "Taskify", 4, "2026-11-12 03:00:00", ""),
        ConfigFile("c2", ".env.staging", ConfigFormat.Env, "staging", "p1", "Taskify", 2, "2026-11-02 03:00:00", ""),
        ConfigFile("c3", "wrangler.json", ConfigFormat.Json, "production", null, null, 1, "2026-10-20 03:00:00", ""),
    )
    private val v3 = "# Supabase\nSUPABASE_URL=https://abc.supabase.co\nSUPABASE_ANON_KEY=\"eyJhbGciOiJIUzI1NiJ9.sample\"\nPOWERSYNC_URL=https://xyz.powersync.journeyapps.com\nSENTRY_DSN=https://k@o1.ingest.sentry.io/2\nDEBUG=0"
    private val v2 = "SUPABASE_URL=https://abc.supabase.co\nSUPABASE_ANON_KEY=\"eyJhbGciOiJIUzI1NiJ9.lama\"\nPOWERSYNC_URL=https://xyz.powersync.journeyapps.com\nDEBUG=1\nLEGACY_API=https://old.example.com"
    val versions = DecryptedVersions(
        listOf(
            DecryptedVersion(ConfigVersionRow("v3", "", "2026-11-12T03:00:00.000Z", 3), ConfigPayload(v3, "Tambah Sentry, matikan debug")),
            DecryptedVersion(ConfigVersionRow("v2", "", "2026-11-05T03:00:00.000Z", 2), ConfigPayload(v2)),
            DecryptedVersion(ConfigVersionRow("v1", "", "2026-10-30T03:00:00.000Z", 1), ConfigPayload("SUPABASE_URL=https://abc.supabase.co")),
        ),
        emptyList(), true,
    )
    val v2Text = v2
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun setup() = shot("sandi_setup_light", false) { SandiContent(SampleSandi.state("none"), SampleSandi.Actions, padding) }

    @Test fun unlockDark() = shot("sandi_unlock_dark", true) { SandiContent(SampleSandi.state("locked"), SampleSandi.Actions, padding) }

    @Test fun vault() = shot("sandi_vault_light", false) { SandiContent(SampleSandi.state(), SampleSandi.Actions, padding) }

    @Test fun codesDark() = shot("sandi_codes_dark", true) { SandiContent(SampleSandi.state(tab = SandiTab.Codes), SampleSandi.Actions, padding) }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun generator() = shot("sandi_generator_light", false) { SandiContent(SampleSandi.state(tab = SandiTab.Generator), SampleSandi.Actions, padding) }

    @Test fun item() = shot("sandi_item_light", false) { ItemDetail(SampleSandi.items[0], SampleSandi.NOW, SampleSandi.Actions, {}, {}) }

    @Test fun settingsDark() = shot("sandi_settings_dark", true) { SettingsBody(SampleSandi.state(), SampleSandi.Actions, {}, {}) }

    @Test fun recoveryKey() = shot("sandi_recovery_key_light", false) {
        RecoveryKeyCard("7K3M-Q9TR-X2VB-H8NC-4WDZ-PF6J-AE", false, {}, {}, {}, {})
    }

    @Test fun configList() = shot("config_list_light", false) {
        VaultContent(SampleVault.state(VaultTab.Config).copy(configs = SampleSandi.configFiles, environments = listOf("production" to 2, "staging" to 1)), SampleVault.Actions, padding, {}, {})
    }

    @Config(qualifiers = "w393dp-h1000dp-xxhdpi")
    @Test fun configFileDark() = shot("config_file_dark", true) {
        ConfigFileContent(ConfigFileUiState(SampleSandi.configFiles[0], true, false, SampleSandi.versions, null), SampleSandi.Actions, padding, {}, {}, {}) { BackButton(it) }
    }

    @Test fun configEditor() = shot("config_editor_light", false) {
        ConfigEditorContent(
            ConfigEditorUiState(isNew = false, loaded = true, locked = false, name = ".env.production", text = SampleSandi.v2Text + "\nBROKEN=\"belum ditutup", baseText = SampleSandi.v2Text),
            SampleSandi.Actions, padding, {}, {}, {},
        ) { BackButton(it) }
    }

    @Test fun configLocked() = shot("config_locked_light", false) {
        ConfigFileContent(ConfigFileUiState(SampleSandi.configFiles[1], true, true), SampleSandi.Actions, padding, {}, {}, {}) { BackButton(it) }
    }
}
