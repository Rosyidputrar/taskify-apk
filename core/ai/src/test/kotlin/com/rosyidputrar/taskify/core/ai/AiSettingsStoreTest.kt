package com.rosyidputrar.taskify.core.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AiSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Pengganti Keystore (Robolectric tidak punya): XOR + base64, cukup untuk memastikan bukan teks biasa. */
    private class FakeBox(var fail: Boolean = false) : SecretBox {
        var destroyed = false
        override fun seal(plain: ByteArray): String { if (fail) throw IllegalStateException("Keystore rusak"); return Base64.getEncoder().encodeToString(plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()) }
        override fun open(sealed: String): ByteArray = Base64.getDecoder().decode(sealed).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun destroy() { destroyed = true }
    }

    private fun prefsXml(): String = File(context.applicationInfo.dataDir, "shared_prefs/taskify_ai.xml").takeIf { it.exists() }?.readText().orEmpty()

    @Test fun settingsArePerProviderAndKeyIsNeverPlainText() {
        val box = FakeBox()
        val store = AiSettingsStore(context, box)
        assertEquals(AiSettings(), store.settings.value)
        store.setBaseUrl("https://openrouter.ai/api/v1/")
        store.setApiKey("  sk-or-rahasia-123  ")
        store.setModels(listOf("a/model", "b/model"))
        store.setModel("a/model")
        assertTrue(store.settings.value.ready)
        assertEquals("sk-or-rahasia-123", store.apiKey())
        assertFalse(prefsXml().contains("sk-or-rahasia-123"))

        store.setProvider(AiProvider.Anthropic)
        assertEquals(AiSettings(provider = AiProvider.Anthropic, baseUrl = "https://api.anthropic.com"), store.settings.value)
        assertNull(store.apiKey())
        store.setApiKey("sk-ant-1")
        store.setProvider(AiProvider.OpenAiCompatible)
        // Kembali: pengaturan OpenRouter utuh.
        assertEquals("https://openrouter.ai/api/v1", store.settings.value.baseUrl)
        assertEquals("a/model", store.settings.value.model)
        assertEquals("sk-or-rahasia-123", store.apiKey())

        // Ganti server → daftar model lama tidak berlaku lagi.
        store.setBaseUrl("https://api.groq.com/openai/v1")
        assertEquals(emptyList(), store.settings.value.models)
        assertEquals(AiErrorKind.Config, assertFailsWith<AiException> { store.setBaseUrl("http://localhost:1234/v1") }.kind)
        assertEquals("https://api.groq.com/openai/v1", store.settings.value.baseUrl) // tidak berubah

        store.setApiKey("")
        assertFalse(store.settings.value.hasKey)
    }

    @Test fun keystoreFailureStoresNothing() {
        val store = AiSettingsStore(context, FakeBox(fail = true))
        val e = assertFailsWith<AiException> { store.setApiKey("sk-1") }
        assertTrue(e.message!!.contains("tidak bisa disimpan aman"))
        assertFalse(store.settings.value.hasKey)
        assertFalse(prefsXml().contains("sk-1"))
    }

    @Test fun systemPromptDefaultIsNotStoredAndWipeForgetsEverything() {
        val box = FakeBox()
        val store = AiSettingsStore(context, box)
        store.setSystemPrompt("  Jawab singkat.  ")
        assertEquals("Jawab singkat.", store.settings.value.systemPrompt)
        store.setSystemPrompt(AiPrompt.DEFAULT)
        assertFalse(prefsXml().contains("system_prompt"))
        store.setDataAccess(true)
        store.setApiKey("sk-1")
        store.wipe()
        assertEquals(AiSettings(), store.settings.value)
        assertTrue(box.destroyed)
    }
}
