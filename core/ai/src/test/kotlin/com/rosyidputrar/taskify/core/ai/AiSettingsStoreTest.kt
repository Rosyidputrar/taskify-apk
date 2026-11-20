package com.rosyidputrar.taskify.core.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
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
        /** Bila true, kunci yang tersimpan tidak bisa dibuka lagi (simulasi Keystore direset). */
        var failOpen: Boolean = false
        override fun seal(plain: ByteArray): String { if (fail) throw IllegalStateException("Keystore rusak"); return Base64.getEncoder().encodeToString(plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()) }
        override fun open(sealed: String): ByteArray? {
            if (failOpen) return null
            return runCatching { Base64.getDecoder().decode(sealed).map { (it.toInt() xor 0x5a).toByte() }.toByteArray() }.getOrNull()
        }
        override fun destroy() { destroyed = true }
    }

    /** Semua yang tersimpan di prefs (kunci + nilai) — dibaca langsung, tidak menunggu tulis berkas asinkron. */
    private fun prefsXml(): String = context.getSharedPreferences("taskify_ai", Context.MODE_PRIVATE).all.toString()

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
        assertEquals(AiErrorKind.Config, assertFailsWith<AiException> { store.setBaseUrl("http://api.openai.com/v1") }.kind) // HTTP publik tetap ditolak
        assertEquals("https://api.groq.com/openai/v1", store.settings.value.baseUrl) // tidak berubah

        store.setApiKey("")
        assertFalse(store.settings.value.hasKey)
    }

    @Test fun unopenableKeyIsReportedInvalidNotSaved() {
        val box = FakeBox()
        val store = AiSettingsStore(context, box)
        store.wipe()
        store.setApiKey("sk-1")
        assertTrue(store.settings.value.hasKey)
        assertFalse(store.settings.value.keyInvalid)
        // Keystore perangkat direset: entri ada tapi tidak bisa dibuka.
        box.failOpen = true
        val reopened = AiSettingsStore(context, box)
        assertFalse(reopened.settings.value.hasKey)
        assertTrue(reopened.settings.value.keyInvalid)
        assertFalse(reopened.settings.value.ready)
        assertNull(reopened.apiKey())
        assertNull(reopened.connection(AiProvider.OpenAiCompatible))
    }

    @Test fun changingBaseUrlClearsSelectedModelToo() {
        val store = AiSettingsStore(context, FakeBox())
        store.wipe()
        store.setModels(listOf("a/model", "b/model"))
        store.setModel("a/model")
        store.setBaseUrl("https://api.groq.com/openai/v1")
        assertEquals(emptyList(), store.settings.value.models)
        assertEquals("", store.settings.value.model)
    }

    @Test fun maxTokensPersistsAndIsClamped() {
        val store = AiSettingsStore(context, FakeBox())
        store.wipe()
        assertEquals(4096, store.settings.value.maxTokens)
        store.setMaxTokens(1234)
        assertEquals(1234, store.settings.value.maxTokens)
        store.setMaxTokens(1)
        assertEquals(256, store.settings.value.maxTokens)
        store.setMaxTokens(999_999)
        assertEquals(128_000, store.settings.value.maxTokens)
        // Bertahan lintas instance.
        assertEquals(128_000, AiSettingsStore(context, FakeBox()).settings.value.maxTokens)
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
