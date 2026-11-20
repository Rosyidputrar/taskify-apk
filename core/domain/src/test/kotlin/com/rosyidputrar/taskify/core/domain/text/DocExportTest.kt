package com.rosyidputrar.taskify.core.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocExportTest {
    private val body = """
        ## Rencana
        Jalan kaki **30 menit**, lalu _menulis_ di [taskify](https://taskify.id).

        - [x] Rapat sprint
        - [ ] Bayar listrik
          - token 200rb
        1. satu
        2. dua

        > Pelan-pelan saja.

        ```kotlin
        val a = 1 < 2
        ```

        | Nama | Nominal |
        |---|---|
        | Listrik | 200rb |

        ---
    """.trimIndent()

    @Test fun markdownKeepsSourceAndAddsTitle() {
        assertEquals("# Notulen\n\nisi\n", DocExport.markdown("  Notulen ", "isi\n\n"))
        assertEquals("isi\n", DocExport.markdown(null, "isi"))
        assertEquals("# Judul\n", DocExport.markdown("Judul", "  "))
        assertTrue(DocExport.markdown("R", body).contains("- [x] Rapat sprint"))
        // Keterangan (tanggal/mood) jadi baris miring di bawah judul.
        assertEquals("# Pagi\n\n_Jumat, 20 November 2026 · Mood Baik_\n\nisi\n", DocExport.markdown("Pagi", "isi", "Jumat, 20 November 2026 · Mood Baik"))
        assertEquals("_Jumat_\n\nisi\n", DocExport.markdown(null, "isi", "Jumat"))
    }

    @Test fun plainTextWithMeta() {
        assertEquals("Pagi\nJumat · Mood Baik\n\nisi\n", DocExport.plainText("Pagi", "isi", "Jumat · Mood Baik"))
        assertEquals("Jumat\n\nisi\n", DocExport.plainText(" ", "isi", "Jumat"))
    }

    @Test fun plainTextDropsMarkdownSyntax() {
        val t = DocExport.plainText("Notulen", body)
        val expected = """
            Notulen

            Rencana

            Jalan kaki 30 menit, lalu menulis di taskify (https://taskify.id).

            ☑ Rapat sprint
            ☐ Bayar listrik
              • token 200rb
            1. satu
            2. dua

            │ Pelan-pelan saja.

            val a = 1 < 2

            Nama | Nominal
            Listrik | 200rb

            ———
        """.trimIndent() + "\n"
        assertEquals(expected, t)
        assertFalse(t.contains("**") || t.contains("[x]") || t.contains("##"))
    }

    @Test fun htmlIsCompleteAndEscaped() {
        val h = DocExport.html("Rapat <tim> & \"kita\"", body, meta = "Ditempel ke Jumat")
        assertTrue(h.startsWith("<!doctype html>"))
        assertTrue(h.contains("<title>Rapat &lt;tim&gt; &amp; &quot;kita&quot;</title>"))
        assertTrue(h.contains("<h1 class=\"title\">Rapat &lt;tim&gt;"))
        assertTrue(h.contains("<p class=\"meta\">Ditempel ke Jumat</p>"))
        assertTrue(h.contains("<h3>Rencana</h3>")) // judul isi turun satu tingkat di bawah judul catatan
        assertTrue(h.contains("<strong>30 menit</strong>") && h.contains("<em>menulis</em>"))
        assertTrue(h.contains("<a href=\"https://taskify.id\">taskify</a>"))
        assertTrue(h.contains("<span class=\"box done\">✓</span> <s>Rapat sprint</s>"))
        assertTrue(h.contains("<code class=\"language-kotlin\">val a = 1 &lt; 2</code>"))
        assertTrue(h.contains("<th>Nama</th>") && h.contains("<td>200rb</td>"))
        assertTrue(h.contains("<blockquote>Pelan-pelan saja.</blockquote>"))
        // Daftar bersarang tertutup rapi: jumlah tag buka = tutup.
        assertEquals(Regex("<ul").findAll(h).count() + Regex("<ol").findAll(h).count(), Regex("</ul>|</ol>").findAll(h).count())
        assertEquals(Regex("<li>").findAll(h).count(), Regex("</li>").findAll(h).count())
        assertTrue(h.contains("<li><span class=\"box\"></span> Bayar listrik<ul>\n<li>token 200rb</li></ul>\n</li></ul>\n<ol>\n<li>satu</li>\n<li>dua</li></ol>"), h)
    }

    @Test fun htmlNeverEmitsScriptOrUnsafeLinks() {
        val h = DocExport.html(null, "<script>alert(1)</script>\n\n[klik](javascript:alert(1)) [web](http://a.b)")
        assertFalse(h.contains("<script>"))
        assertTrue(h.contains("&lt;script&gt;"))
        assertFalse(h.contains("href=\"javascript"))
        assertTrue(h.contains("<a href=\"http://a.b\">web</a>"))
        assertTrue(h.contains("<title>Catatan</title>"))
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("Notulen rapat.pdf", DocExport.fileName("Notulen rapat", "", ShareFormat.Pdf))
        assertEquals("a b c d.md", DocExport.fileName(" a/b:c*d? ", "", ShareFormat.Markdown))
        assertEquals("Catatan.txt", DocExport.fileName(null, "", ShareFormat.Text))
        assertEquals("Catatan.html", DocExport.fileName("...", "", ShareFormat.Html))
        assertTrue(DocExport.fileName(null, "Belanja mingguan: telur, susu", ShareFormat.Text).startsWith("Belanja mingguan"))
        assertEquals(64, DocExport.fileName("x".repeat(200), "", ShareFormat.Pdf).length) // 60 + ".pdf"
        assertEquals("Pagi.png", DocExport.fileName("Pagi", "", ShareFormat.Image))
        assertEquals("snippet.kt", DocExport.fileName("", "", "kt", fallback = "snippet"))
    }
}
