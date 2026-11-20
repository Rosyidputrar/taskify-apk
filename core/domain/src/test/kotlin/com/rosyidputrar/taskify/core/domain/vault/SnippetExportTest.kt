package com.rosyidputrar.taskify.core.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetExportTest {
    @Test fun extensionsFollowLanguage() {
        assertEquals("kt", SnippetExport.extension("kotlin"))
        assertEquals("py", SnippetExport.extension("python"))
        assertEquals("ts", SnippetExport.extension("typescript"))
        assertEquals("txt", SnippetExport.extension("plaintext"))
        assertEquals("txt", SnippetExport.extension("bahasa-aneh"))
        assertEquals("useDebounce.ts", SnippetExport.fileName("useDebounce", "typescript"))
        assertEquals("Query laporan bulanan.sql", SnippetExport.fileName("Query laporan/bulanan", "sql"))
        assertEquals("snippet.kt", SnippetExport.fileName("  ", "kotlin"))
        assertEquals("useDebounce.md", SnippetExport.markdownFileName("useDebounce"))
    }

    @Test fun markdownFencesCode() {
        assertEquals(
            "# Halo\n\nCetak salam.\n\n```kotlin\nprintln(\"hai\")\n```\n",
            SnippetExport.markdown("Halo", "kotlin", "println(\"hai\")\n\n", "Cetak salam."),
        )
        // Teks biasa tanpa nama bahasa; deskripsi kosong dilewati.
        assertEquals("# A\n\n```\nx\n```\n", SnippetExport.markdown("A", "plaintext", "x", "  "))
    }

    @Test fun fenceNeverCollidesWithBackticksInCode() {
        val md = SnippetExport.markdown("Contoh", "markdown", "```js\nx()\n```", null)
        assertEquals("# Contoh\n\n````markdown\n```js\nx()\n```\n````\n", md)
    }

    @Test fun sourceEndsWithSingleNewline() {
        assertEquals("a\nb\n", SnippetExport.source("a\nb\n\n\n"))
        assertEquals("a\n", SnippetExport.source("a"))
    }
}
