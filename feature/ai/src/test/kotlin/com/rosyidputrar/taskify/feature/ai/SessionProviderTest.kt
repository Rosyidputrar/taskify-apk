package com.rosyidputrar.taskify.feature.ai

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regresi E-M4: `setModel` memakai provider SESI, bukan provider pengaturan global.
 * (Tes end-to-end lewat AiChatViewModel + AiRepository sungguhan tidak bisa jalan di modul
 * Android ini: PowerSync varian Android butuh SQLite native yang tidak ada di tes unit JVM —
 * lihat status area E. Aturan pemilihannya diuji di sini; `setModel` di repository sendiri
 * sudah diuji `AiRepositoryTest` di modul JVM `core:store`.)
 */
class SessionProviderTest {
    @Test fun sessionProviderWinsOverSettingsProvider() {
        assertEquals("Anthropic", resolveSessionProvider("Anthropic", "OpenAiCompatible"))
        assertEquals("OpenAiCompatible", resolveSessionProvider("OpenAiCompatible", "Anthropic"))
    }

    @Test fun missingSessionProviderFallsBackToSettings() {
        assertEquals("OpenAiCompatible", resolveSessionProvider(null, "OpenAiCompatible"))
        assertEquals("OpenAiCompatible", resolveSessionProvider("", "OpenAiCompatible"))
        assertEquals("OpenAiCompatible", resolveSessionProvider("   ", "OpenAiCompatible"))
    }
}
