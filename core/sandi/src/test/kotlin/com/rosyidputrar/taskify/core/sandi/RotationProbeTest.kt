package com.rosyidputrar.taskify.core.sandi

import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.store.sandi.EncRow
import org.junit.Test
import kotlin.test.assertEquals

/**
 * D-11: probe rotasi menghitung baris yang TIDAK bisa dibuka dengan DEK saat ini — baris bagus
 * (DEK sama), baris berkunci lain, dan baris rusak dibedakan dengan benar.
 */
class RotationProbeTest {
    private val userId = "user-1"
    private val dek = Dek(SandiCrypto.randomBytes(32))
    private val otherDek = Dek(SandiCrypto.randomBytes(32))

    private fun row(id: String, enc: String) = EncRow(id, enc, "", "")

    @Test fun countsOnlyRowsThatFailToDecrypt() {
        val good = row("a", SandiCrypto.encryptItem(dek, userId, "a", ItemPayload("GitHub", "", "u", "p"), "0123456789abcdef"))
        val otherKey = row("b", SandiCrypto.encryptItem(otherDek, userId, "b", ItemPayload("Bank", "", "u", "p"), "0123456789abcdef"))
        val corrupt = row("c", "bukan-sandi-terenkripsi")
        val rows = listOf(good, otherKey, corrupt)
        assertEquals(2, countUndecryptable(rows) { r -> SandiCrypto.decryptItem(dek, userId, r.id, r.enc) })
        assertEquals(0, countUndecryptable(listOf(good)) { r -> SandiCrypto.decryptItem(dek, userId, r.id, r.enc) })
        assertEquals(0, countUndecryptable(emptyList<EncRow>()) { r -> SandiCrypto.decryptItem(dek, userId, r.id, r.enc) })
    }

    @Test fun rotationAtRiskTotalSumsCategories() {
        assertEquals(3, SandiService.RotationAtRisk(items = 2, log = 1, configs = 0).total)
        assertEquals(0, SandiService.RotationAtRisk(0, 0, 0).total)
    }
}
