package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.ai.AiProvider
import com.rosyidputrar.taskify.core.ai.AiSettings
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.store.ai.ChatSession
import com.rosyidputrar.taskify.core.store.ai.ChatState
import com.rosyidputrar.taskify.feature.ai.AiChatContent
import com.rosyidputrar.taskify.feature.ai.AiSettingsActions
import com.rosyidputrar.taskify.feature.ai.AiSettingsContent
import com.rosyidputrar.taskify.feature.ai.AiSettingsUi
import com.rosyidputrar.taskify.feature.ai.ChatActions
import com.rosyidputrar.taskify.feature.ai.ChatItem
import com.rosyidputrar.taskify.feature.ai.ChatMarkdown
import com.rosyidputrar.taskify.feature.ai.ChatUiState
import com.rosyidputrar.taskify.feature.ai.ModelPicker
import com.rosyidputrar.taskify.feature.ai.SessionsActions
import com.rosyidputrar.taskify.feature.ai.SessionsContent
import com.rosyidputrar.taskify.feature.ai.SessionsUiState
import com.rosyidputrar.taskify.feature.ai.ToolActivity
import com.rosyidputrar.taskify.feature.ai.ToolPreview
import com.rosyidputrar.taskify.feature.ai.ToolStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

object SampleAi {
    val markdown = """
        ## Ringkasan minggu ini

        Kamu punya **5 tugas**, 2 di antaranya *terlambat* dan satu ~~sudah dibatalkan~~.

        | Tugas | Tenggat | Status |
        |---|---|---|
        | Bayar listrik | Sen, 23 Nov | Belum |
        | Laporan Q4 | Rab, 25 Nov | Selesai |

        - [x] Kirim proposal
        - [ ] Rapat tim

        > Tips: kerjakan yang paling kecil dulu.

        ```kotlin
        fun total(xs: List<Long>): Long = xs.sum() // dalam rupiah
        ```

        Tekan `Ctrl+K` untuk mencari. Detail di [panduan](https://example.com).
    """.trimIndent()

    val items = listOf(
        ChatItem.User("u1", "Apa saja tugasku minggu ini? Tolong buatkan tabel.", editable = false),
        ChatItem.Assistant("a1", "", ChatState.Done, null, false, listOf(ToolActivity("c1", "tugas", "Membaca tugas", ToolStatus.Done, result = "5 tugas minggu ini …")), last = false),
        ChatItem.Assistant("a2", markdown, ChatState.Done, null, false, emptyList(), last = false),
        ChatItem.User("u2", "Buatkan tugas bayar listrik besok jam 9", editable = true),
        ChatItem.Assistant(
            "a3", "Siap, ini rinciannya:", ChatState.Awaiting, null, false,
            listOf(ToolActivity("w1", "buat_tugas", "Membuat tugas", ToolStatus.Awaiting, preview = ToolPreview("Buat tugas?", listOf("Judul" to "Bayar listrik", "Tenggat" to "Sab, 21 Nov 2026 09.00", "Prioritas" to "Tinggi")))),
            last = true,
        ),
    )

    fun chat(items: List<ChatItem> = SampleAi.items, ready: Boolean = true, running: Boolean = false, dataAccess: Boolean = true) = ChatUiState(
        sessionId = "s1", title = "Tugas minggu ini", items = items, running = running, ready = ready,
        provider = AiProvider.OpenAiCompatible, model = "gpt-4.1-mini", host = "api.openai.com", dataAccess = dataAccess,
        models = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "o4-mini"), systemPrompt = "Kamu asisten.", loaded = true,
    )

    val errorItems = listOf(
        ChatItem.User("u1", "Halo", editable = true),
        ChatItem.Assistant("a1", "", ChatState.Error, "API key ditolak (401). Periksa kuncinya. Pesan server: Incorrect API key provided.", false, emptyList(), last = true),
    )

    val sessions = listOf(
        ChatSession("s1", "Tugas minggu ini", "OpenAiCompatible", "gpt-4.1-mini", "", true, 0, System.currentTimeMillis() - 5 * 60_000, "Siap, ini rinciannya:"),
        ChatSession("s2", "Rencana anggaran Desember", "Anthropic", "claude-sonnet", "", false, 0, System.currentTimeMillis() - 3 * 3_600_000, "Berikut rencana anggaran dengan pembagian 50/30/20 …"),
        ChatSession("s3", "Belajar bunga majemuk", "OpenAiCompatible", "gpt-4o", "", false, 0, 1_763_000_000_000, "Bunga majemuk berarti bunga dihitung dari pokok ditambah bunga sebelumnya."),
    )

    val settings = AiSettingsUi(
        settings = AiSettings(AiProvider.OpenAiCompatible, "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini", listOf("anthropic/claude-sonnet", "google/gemini-flash", "openai/gpt-4.1-mini"), dataAccess = false, hasKey = true),
        testOk = true, testMessage = "Tersambung · 3 model tersedia.", chats = 3,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class AiScreensTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues()

    private class Recorder : ChatActions, SessionsActions, AiSettingsActions {
        val calls = mutableListOf<String>()
        override fun send(text: String) { calls += "send:$text" }
        override fun stop() { calls += "stop" }
        override fun retry() { calls += "retry" }
        override fun editLast(text: String) { calls += "edit:$text" }
        override fun decide(callId: String, approve: Boolean) { calls += "decide:$callId:$approve" }
        override fun setDataAccess(on: Boolean) { calls += "access:$on" }
        override fun setModel(model: String) { calls += "model:$model" }
        override fun saveBaseUrl(raw: String) { calls += "url:$raw" }
    }

    private fun chat(state: ChatUiState, actions: Recorder = Recorder()) = compose.setContent {
        TaskifyTheme { AiChatContent(state, actions, padding, onBack = {}, backButton = {}, onSettings = {}, onDeleted = {}) }
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    // ------------------------------------------------------------------ perilaku

    @Test fun sendingClearsTheComposer() {
        val actions = Recorder()
        chat(SampleAi.chat(items = emptyList()), actions)
        compose.onNodeWithContentDescription("Kirim").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("  Ringkas pengeluaranku  ")
        compose.onNodeWithContentDescription("Kirim").assertIsEnabled().performClick()
        assertEquals(listOf("send:Ringkas pengeluaranku"), actions.calls)
        compose.onNodeWithText("Ringkas pengeluaranku").assertDoesNotExist()
    }

    @Test fun runningShowsStopInsteadOfSend() {
        val actions = Recorder()
        chat(SampleAi.chat(running = true), actions)
        compose.onNodeWithContentDescription("Kirim").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hentikan jawaban").performClick()
        assertEquals(listOf("stop"), actions.calls)
    }

    @Test fun notConfiguredShowsSetupAndBlocksInput() {
        chat(SampleAi.chat(items = emptyList(), ready = false))
        compose.onNodeWithText("Hubungkan penyedia AI dan pilih model dulu.").assertExists()
        compose.onNodeWithText("Atur AI dulu di Pengaturan").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist() // kolom nonaktif tidak bisa diketik
        compose.onNodeWithContentDescription("Kirim").assertIsNotEnabled()
    }

    @Test fun writeToolNeedsApprovalAndEmptySuggestionsFillInput() {
        val actions = Recorder()
        chat(SampleAi.chat(), actions)
        compose.onNodeWithText("Buat tugas?").performScrollTo().assertExists()
        compose.onNodeWithText("Bayar listrik").assertExists()
        compose.onNodeWithText("Simpan").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        assertEquals(listOf("decide:w1:true", "decide:w1:false"), actions.calls)
    }

    @Test fun errorCanBeRetried() {
        val actions = Recorder()
        chat(SampleAi.chat(items = SampleAi.errorItems), actions)
        compose.onNodeWithText("API key ditolak (401)", substring = true).assertExists()
        compose.onNodeWithText("Coba lagi").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test fun dataAccessNeedsConsentToTurnOnButNotOff() {
        val actions = Recorder()
        chat(SampleAi.chat(items = emptyList(), dataAccess = false), actions)
        compose.onNodeWithText("Akses data: mati").performClick()
        compose.onNodeWithText("Data yang dibaca ikut dikirim ke api.openai.com.").assertExists()
        compose.onNodeWithText("Izinkan").performClick()
        assertEquals(listOf("access:true"), actions.calls)
    }

    @Test fun editingLastMessage() {
        val actions = Recorder()
        chat(SampleAi.chat(items = SampleAi.errorItems), actions)
        compose.onNodeWithText("Halo").performTouchInput { longClick() }
        compose.onNodeWithText("Ubah").performClick()
        compose.onNodeWithText("Mengubah pesan").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput(" dunia")
        compose.onNodeWithContentDescription("Kirim").performClick()
        assertEquals(listOf("edit:Halo dunia"), actions.calls)
    }

    @Test fun emptyChatSuggestionsFillTheComposer() {
        chat(SampleAi.chat(items = emptyList()))
        compose.onNodeWithText("Apa saja tugasku minggu ini?").performClick()
        // Chip + kolom input sekarang memuat teks yang sama; kursor di akhir → ketikan menyambung.
        compose.onNode(hasSetTextAction()).performTextInput(" Urutkan per tenggat.")
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("Apa saja tugasku minggu ini? Urutkan per tenggat.")).assertExists()
    }

    @Test fun streamingRevealsWholeSentencesOnly() {
        compose.setContent { TaskifyTheme { ChatMarkdown("Halo Rosyid. Ini kalimat yang belum sel", streaming = true) } }
        compose.onNodeWithText("Halo Rosyid.", substring = true).assertExists()
        compose.onNodeWithText("belum sel", substring = true).assertDoesNotExist()
    }

    @Test fun modelPickerFiltersAndAcceptsManualNames() {
        val picked = mutableListOf<String>()
        compose.setContent { TaskifyTheme { ModelPicker(listOf("gpt-4o", "gpt-4.1-mini", "o4-mini"), "gpt-4o", "4.1", {}, { picked += it }, {}) } }
        compose.onNodeWithText("gpt-4.1-mini").performClick()
        compose.onNodeWithText("o4-mini").assertDoesNotExist()
        compose.onNodeWithText("Pakai \"4.1\"").performClick()
        assertEquals(listOf("gpt-4.1-mini", "4.1"), picked)
    }

    @Test fun settingsPresetFillsBaseUrl() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { AiSettingsContent(SampleAi.settings, actions, padding, onBack = null, backButton = {}) } }
        compose.onNodeWithText("Groq").performScrollTo().performClick()
        compose.onNodeWithText("Simpan alamat").performScrollTo().performClick()
        assertEquals(listOf("url:https://api.groq.com/openai/v1"), actions.calls)
        compose.onNodeWithText("Tersimpan terenkripsi di perangkat ini").performScrollTo().assertExists()
    }

    // ------------------------------------------------------------------ screenshot

    @Config(qualifiers = "w393dp-h2400dp-xxhdpi")
    @Test fun chatLight() = shot("ai_chat_light") { AiChatContent(SampleAi.chat(), Recorder(), padding, null, {}, {}, {}) }

    @Config(qualifiers = "w393dp-h2400dp-xxhdpi")
    @Test fun chatDark() = shot("ai_chat_dark", dark = true) { AiChatContent(SampleAi.chat(), Recorder(), padding, null, {}, {}, {}) }

    @Test fun chatEmpty() = shot("ai_chat_empty_light") { AiChatContent(SampleAi.chat(items = emptyList(), dataAccess = false), Recorder(), padding, null, {}, {}, {}) }

    @Test fun chatError() = shot("ai_chat_error_light") { AiChatContent(SampleAi.chat(items = SampleAi.errorItems), Recorder(), padding, null, {}, {}, {}) }

    @Test fun sessions() = shot("ai_sessions_light") {
        SessionsContent(SessionsUiState(SampleAi.sessions, loaded = true, ready = true, running = setOf("s2")), Recorder(), padding, null, {}, {}, null, {})
    }

    @Test fun sessionsSetup() = shot("ai_sessions_setup_dark", dark = true) {
        SessionsContent(SessionsUiState(emptyList(), loaded = true, ready = false), Recorder(), padding, null, {}, {}, null, {})
    }

    @Config(qualifiers = "w393dp-h2200dp-xxhdpi")
    @Test fun settings() = shot("ai_settings_light") { AiSettingsContent(SampleAi.settings, Recorder(), padding, null, {}) }

    /** Tablet lanskap: daftar + chat berdampingan, kolom chat dibatasi lebarnya. */
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun tabletTwoPane() = shot("ai_tablet_light") {
        Row {
            SessionsContent(SessionsUiState(SampleAi.sessions, loaded = true, ready = true), Recorder(), padding, "s1", {}, {}, null, {}, Modifier.width(360.dp).fillMaxHeight())
            VerticalDivider()
            AiChatContent(SampleAi.chat(), Recorder(), padding, null, {}, {}, {})
        }
    }
}
