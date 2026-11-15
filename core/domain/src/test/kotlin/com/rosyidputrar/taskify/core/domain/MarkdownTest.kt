package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.domain.text.MdBlock
import com.rosyidputrar.taskify.core.domain.text.MdStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kasus diambil dari `markdown.test.ts` webapp + pengurai pratinjau native. */
class MarkdownTest {
    @Test fun excerptStripsSyntax() {
        assertEquals("Judul tebal dan miring selesai", Markdown.plainExcerpt("## Judul\n- **tebal** dan _miring_\n- [x] selesai"))
        assertEquals("lihat situs akhir", Markdown.plainExcerpt("lihat [situs](https://x.id) ![img](a.png) ```kode``` akhir"))
    }

    @Test fun excerptCutsWithEllipsis() {
        val out = Markdown.plainExcerpt("kata ".repeat(100), 20)
        assertTrue(out.length <= 20)
        assertTrue(out.endsWith("…"))
        assertEquals("halo", Markdown.plainExcerpt("halo"))
        assertEquals("Awal cerita.…", Markdown.plainExcerpt("**Awal** cerita. " + "isi ".repeat(50_000), 14))
        assertEquals(Markdown.plainExcerpt("## A\nb", 50), Markdown.plainExcerpt("## A\nb", 50))
        assertEquals("", Markdown.plainExcerpt("   "))
    }

    @Test fun wordCount() {
        assertEquals(3, Markdown.wordCount("  satu   dua\ntiga "))
        assertEquals(0, Markdown.wordCount(""))
        assertEquals(0, Markdown.wordCount("   "))
    }

    @Test fun toggleChecklist() {
        val md = "- [ ] a\n- [x] b\n- biasa"
        assertEquals("- [x] a\n- [x] b\n- biasa", Markdown.toggleChecklistLine(md, 1))
        assertEquals("- [ ] a\n- [ ] b\n- biasa", Markdown.toggleChecklistLine(md, 2))
        assertEquals(md, Markdown.toggleChecklistLine(md, 3))
        assertEquals(md, Markdown.toggleChecklistLine(md, 9))
        assertEquals("  1. [x] langkah", Markdown.toggleChecklistLine("  1. [ ] langkah", 1))
        assertEquals(1 to 2, Markdown.checklistProgress(md))
    }

    @Test fun blocksKeepSourceLines() {
        val md = "# Judul\n\nparagraf satu\nbaris dua\n\n- [ ] beli susu\n- [x] bayar listrik\n  - anak\n1. pertama\n> kutipan\n> lanjut\n---\n```kt\nval x = 1\n```\n| a | b |\n|---|---|\n| 1 | 2 |"
        val blocks = Markdown.parseBlocks(md)
        assertEquals(MdBlock.Heading(1, "Judul", 1), blocks[0])
        assertEquals(MdBlock.Paragraph("paragraf satu\nbaris dua", 3), blocks[1])
        assertEquals(MdBlock.ListItem(0, null, false, "beli susu", 6), blocks[2])
        assertEquals(MdBlock.ListItem(0, null, true, "bayar listrik", 7), blocks[3])
        assertEquals(MdBlock.ListItem(1, null, null, "anak", 8), blocks[4])
        assertEquals(MdBlock.ListItem(0, 1, null, "pertama", 9), blocks[5])
        assertEquals(MdBlock.Quote("kutipan\nlanjut", 10), blocks[6])
        assertEquals(MdBlock.Rule(12), blocks[7])
        assertEquals(MdBlock.Code("val x = 1", "kt", 13), blocks[8])
        assertEquals(MdBlock.Table(listOf("a", "b"), listOf(listOf("1", "2")), 16), blocks[9])
        // Baris checklist dari blok bisa langsung dipakai untuk mencentang di teks asli.
        val item = blocks[2] as MdBlock.ListItem
        assertTrue(Markdown.toggleChecklistLine(md, item.line).contains("- [x] beli susu"))
    }

    @Test fun inlineStyles() {
        val spans = Markdown.parseInline("ini **tebal _dan miring_** lalu `kode` dan ~~coret~~ [situs](https://x.id)")
        assertEquals("ini ", spans[0].text)
        assertEquals(MdStyle(bold = true), spans[1].style)
        assertEquals("dan miring", spans[2].text)
        assertEquals(MdStyle(bold = true, italic = true), spans[2].style)
        assertTrue(spans.any { it.text == "kode" && it.style.code })
        assertTrue(spans.any { it.text == "coret" && it.style.strike })
        assertTrue(spans.any { it.text == "situs" && it.style.link == "https://x.id" })
    }

    @Test fun inlineLeavesPlainTextAlone() {
        assertEquals("snake_case_name dan 2 * 3 * 4", Markdown.parseInline("snake_case_name dan 2 * 3 * 4").joinToString("") { it.text })
        assertEquals(1, Markdown.parseInline("snake_case_name").size)
        assertEquals("**belum ditutup", Markdown.parseInline("**belum ditutup").single().text)
        val auto = Markdown.parseInline("buka https://taskify.id/a, ya")
        assertEquals("https://taskify.id/a", auto.first { it.style.link != null }.text)
        assertEquals("*bukan miring*", Markdown.parseInline("\\*bukan miring\\*").single().text)
    }
}
