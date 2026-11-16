package com.rosyidputrar.taskify.feature.finance

import com.rosyidputrar.taskify.core.domain.finance.TxKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Draf editor transaksi bertahan saat sistem mematikan proses (SavedStateHandle). */
class TxDraftSavedTest {
    @Test fun roundTripsEveryEditableField() {
        val d = TxDraft(
            loaded = true, id = "t1", kind = TxKind.Transfer, amountText = "25rb", walletId = "w1", toWalletId = "w2",
            categoryId = null, date = "2026-11-16", time = "08:30", title = "Isi saldo", note = "e-wallet", linked = "Dicatat dari transaksi berulang.",
        )
        val back = TxDraft.fromSaved(d.toSaved())
        assertEquals(d.editable(), back.editable())
        assertEquals("t1", back.id)
        assertEquals(d.linked, back.linked)
        assertEquals(true, back.loaded)
    }

    @Test fun editsAreDetected() {
        val d = TxDraft(loaded = true, amountText = "10000", walletId = "w1", date = "2026-11-16")
        assertNotEquals(d.editable(), d.copy(note = "parkir").editable())
        assertEquals(d.editable(), d.copy(error = "salah").editable(), "pesan error bukan perubahan")
    }
}
