package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.files.AutoAttempt
import com.rosyidputrar.taskify.core.domain.files.MAX_AUTO_RETRIES
import com.rosyidputrar.taskify.core.domain.files.MIN_RETRY_GAP_MS
import com.rosyidputrar.taskify.core.domain.files.WarningLevel
import com.rosyidputrar.taskify.core.domain.files.ZipEntryInfo
import com.rosyidputrar.taskify.core.domain.files.buildTree
import com.rosyidputrar.taskify.core.domain.files.canAutoRetry
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.files.isImagePath
import com.rosyidputrar.taskify.core.domain.files.isTextPath
import com.rosyidputrar.taskify.core.domain.files.nextVersionLabel
import com.rosyidputrar.taskify.core.domain.files.storageKey
import com.rosyidputrar.taskify.core.domain.files.summarizeZip
import com.rosyidputrar.taskify.core.domain.files.unwrapSingleRoot
import com.rosyidputrar.taskify.core.domain.vault.Highlighter
import com.rosyidputrar.taskify.core.domain.vault.TokenType
import com.rosyidputrar.taskify.core.domain.vault.fillTemplate
import com.rosyidputrar.taskify.core.domain.vault.languageFromPath
import com.rosyidputrar.taskify.core.domain.vault.languageLabel
import com.rosyidputrar.taskify.core.domain.vault.templateVariables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultFilesTest {
    private val user = "11111111-1111-1111-1111-111111111111"
    private val att = "22222222-2222-2222-2222-222222222222"

    @Test fun storageKeyMatchesEdgeFunctionRules() {
        assertEquals("$user/$att/struk.jpg", storageKey(user, att, "struk.jpg"))
        assertEquals("$user/$att/a_b_c.txt", storageKey(user, att, "a/b\\c.txt"))
        assertEquals("$user/$att/_env", storageKey(user, att, ".env"))
        assertEquals("$user/$att/file", storageKey(user, att, ""))
        assertEquals(200, storageKey(user, att, "x".repeat(300) + ".zip").substringAfterLast('/').length)
        // Format yang diterima Edge Function `files`.
        assertTrue(Regex("^[0-9a-f-]{36}/[0-9a-f-]{36}/[^/]{1,255}$").matches(storageKey(user, att, "../../etc/passwd")))
    }

    @Test fun bytesAndRetryPolicy() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1,5 KB", formatBytes(1536))
        assertEquals("100 MB", formatBytes(100L * 1024 * 1024))
        assertEquals("–", formatBytes(null))
        val t0 = 1_000_000L
        assertTrue(canAutoRetry(null, t0))
        assertFalse(canAutoRetry(AutoAttempt(1, t0), t0 + 5_000))
        assertTrue(canAutoRetry(AutoAttempt(1, t0), t0 + MIN_RETRY_GAP_MS))
        assertFalse(canAutoRetry(AutoAttempt(MAX_AUTO_RETRIES, t0), t0 + 10 * MIN_RETRY_GAP_MS))
    }

    @Test fun zipWarningsAndTree() {
        val entries = listOf(
            ZipEntryInfo("app/", 0, true),
            ZipEntryInfo("app/.env", 10, false),
            ZipEntryInfo("app/.env.example", 10, false),
            ZipEntryInfo("app/node_modules/a/index.js", 100, false),
            ZipEntryInfo("app/node_modules/b/index.js", 100, false),
            ZipEntryInfo("app/.git/HEAD", 5, false),
            ZipEntryInfo("app/dist/main.js", 50, false),
            ZipEntryInfo("app/src/Main.kt", 20, false),
            ZipEntryInfo("app/README.md", 7, false),
        )
        val s = summarizeZip(entries)
        assertEquals(8, s.files)
        assertEquals(302, s.totalSize)
        assertEquals(WarningLevel.Danger, s.warnings[0].level)
        assertEquals(listOf("app/.env"), s.warnings[0].paths)
        assertEquals(listOf("app/node_modules"), s.warnings[1].paths)
        assertEquals(listOf("app/.git"), s.warnings[2].paths)
        assertEquals(listOf("app/dist"), s.warnings[3].paths)

        val root = buildTree(entries).unwrapSingleRoot()
        assertEquals("app", root.name)
        assertEquals(listOf(".git", "dist", "node_modules", "src", ".env", ".env.example", "README.md"), root.children.map { it.name })
        assertEquals(200, root.children.first { it.name == "node_modules" }.size)
    }

    @Test fun pathsVersionsLanguages() {
        assertTrue(isTextPath("src/App.tsx"))
        assertTrue(isTextPath("Dockerfile"))
        assertTrue(isTextPath(".env.local"))
        assertFalse(isTextPath("logo.png"))
        assertTrue(isImagePath("logo.PNG"))
        assertEquals("v1", nextVersionLabel(listOf(null, "rilis")))
        assertEquals("v3", nextVersionLabel(listOf("v1", "v2")))
        assertEquals("v1.3", nextVersionLabel(listOf("v1.2", "1.1")))
        assertEquals("v2.1", nextVersionLabel(listOf("v1.9", "v2.0")))
        // Label numerik raksasa diabaikan, bukan crash (dulu: NumberFormatException di toInt()).
        assertEquals("v3", nextVersionLabel(listOf("v2", "v99999999999999999999")))
        assertEquals("v1", nextVersionLabel(listOf("v99999999999999999999")))
        assertEquals("v1", nextVersionLabel(listOf("v2147483647")))
        assertEquals("v1.3", nextVersionLabel(listOf("v1.2", "v1.99999999999999999999")))
        assertEquals("docker", languageFromPath("x/Dockerfile"))
        assertEquals("ini", languageFromPath(".env.production"))
        assertEquals("kotlin", languageFromPath("Main.kt"))
        assertEquals("plaintext", languageFromPath("LICENSE"))
        assertEquals("Kotlin", languageLabel("kotlin"))
    }

    @Test fun templates() {
        val code = "curl {{ host }}/api?token={{token}}&h={{host}}"
        assertEquals(listOf("host", "token"), templateVariables(code))
        assertEquals("curl x.id/api?token=abc&h=x.id", fillTemplate(code, mapOf("host" to "x.id", "token" to "abc")))
        assertEquals("{{a}}", fillTemplate("{{a}}", emptyMap()))
    }

    @Test fun highlighter() {
        val t = Highlighter.tokenize("val x = \"hi\" // komentar\nfun f() = 42", "kotlin")
        assertEquals("val x = \"hi\" // komentar\nfun f() = 42", t.joinToString("") { it.text })
        assertTrue(t.any { it.type == TokenType.Keyword && it.text == "val" })
        assertTrue(t.any { it.type == TokenType.String && it.text == "\"hi\"" })
        assertTrue(t.any { it.type == TokenType.Comment && it.text == "// komentar" })
        assertTrue(t.any { it.type == TokenType.Number && it.text == "42" })
        assertTrue(Highlighter.tokenize("# judul\necho 'a'", "bash").first().type == TokenType.Comment)
        assertTrue(Highlighter.tokenize("SELECT 1 -- x", "sql").any { it.type == TokenType.Comment })
        assertTrue(Highlighter.tokenize("<div class=\"a\">hi</div>", "markup").first().type == TokenType.Tag)
        assertEquals(1, Highlighter.tokenize("apa saja", "plaintext").size)
    }
}
