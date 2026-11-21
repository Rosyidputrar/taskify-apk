package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.editor.EditState
import com.rosyidputrar.taskify.core.domain.editor.MarkdownAction
import com.rosyidputrar.taskify.core.domain.editor.MarkdownEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkdownEditTest {
    @Test fun wrapSelectionKeepsOuterSpaces() {
        val out = MarkdownEdit.apply(EditState("halo dunia ", 4, 11), MarkdownAction.Bold)
        assertEquals("halo **dunia** ", out.text)
        assertEquals(7, out.start)
        assertEquals(12, out.end)
    }

    @Test fun wrapWithoutSelectionInsertsPlaceholder() {
        val out = MarkdownEdit.apply(EditState("ab", 1), MarkdownAction.Link)
        assertEquals("a[teks](https://)b", out.text)
        assertEquals("teks", out.text.substring(out.start, out.end))
    }

    @Test fun linePrefixToggles() {
        val on = MarkdownEdit.apply(EditState("satu\ndua", 6), MarkdownAction.Checklist)
        assertEquals("satu\n- [ ] dua", on.text)
        assertEquals(12, on.start)
        val off = MarkdownEdit.apply(on, MarkdownAction.Checklist)
        assertEquals("satu\ndua", off.text)
        // Daftar biasa → checklist mengganti penanda, tidak menumpuk.
        assertEquals("- [ ] item", MarkdownEdit.apply(EditState("- item", 3), MarkdownAction.Checklist).text)
        // Seleksi banyak baris.
        assertEquals("- a\n- b", MarkdownEdit.apply(EditState("a\nb", 0, 3), MarkdownAction.Bullet).text)
    }

    private fun enter(text: String, pos: Int = text.length): Pair<EditState, EditState> {
        val prev = EditState(text, pos)
        return prev to EditState(text.substring(0, pos) + "\n" + text.substring(pos), pos + 1)
    }

    @Test fun enterContinuesLists() {
        val (p1, n1) = enter("- [x] beli")
        assertEquals("- [x] beli\n- [ ] ", MarkdownEdit.continueList(p1, n1)!!.text)
        val (p2, n2) = enter("  3. tiga")
        assertEquals("  3. tiga\n  4. ", MarkdownEdit.continueList(p2, n2)!!.text)
        val (p3, n3) = enter("a\n- ")
        val ended = MarkdownEdit.continueList(p3, n3)!!
        assertEquals("a\n", ended.text)
        assertEquals(2, ended.start)
        val (p4, n4) = enter("paragraf biasa")
        assertNull(MarkdownEdit.continueList(p4, n4))
        // Bukan ketikan Enter (mis. tempel) → tidak diubah.
        assertNull(MarkdownEdit.continueList(EditState("- a", 3), EditState("- a\nb", 5)))
        // Angka 10+ digit dan overflow Long tidak boleh crash NumberFormatException.
        val (pBig, nBig) = enter("12345678901. item besar")
        assertEquals("12345678901. item besar\n12345678902. ", MarkdownEdit.continueList(pBig, nBig)?.text)
        val (pHuge, nHuge) = enter("99999999999999999999999999999. terlalu besar")
        assertNull(MarkdownEdit.continueList(pHuge, nHuge))
    }
}
