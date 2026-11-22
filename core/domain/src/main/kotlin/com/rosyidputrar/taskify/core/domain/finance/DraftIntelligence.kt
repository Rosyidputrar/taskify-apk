package com.rosyidputrar.taskify.core.domain.finance

/**
 * Kecerdasan lokal untuk draf transaksi dari notifikasi:
 * - Normalisasi kunci merchant/pengirim agar kategori yang pernah dipilih pengguna diingat otomatis.
 * - Pencocokan transaksi masuk/keluar dengan catatan hutang/piutang terbuka (1 ketukan tandai lunas).
 */
object DraftIntelligence {
    private val NON_ALNUM = Regex("[^a-z0-9 ]+")
    private val SPACES = Regex("\\s+")

    /** Normalisasi nama merchant/pengirim menjadi kunci stabil (null bila terlalu pendek/kosong). */
    fun normalizeMerchantKey(raw: String?): String? {
        val cleaned = raw?.lowercase()
            ?.replace(NON_ALNUM, " ")
            ?.replace(SPACES, " ")
            ?.trim()
            .orEmpty()
        return cleaned.takeIf { it.length >= 2 }
    }

    /**
     * Cari catatan hutang/piutang yang belum lunas dan cocok kuat dengan draf notifikasi:
     * - Uang masuk ([TxKind.Income]) → Piutang ([DebtDirection.Lent])
     * - Uang keluar ([TxKind.Expense]) → Hutang saya ([DebtDirection.Borrowed])
     * - Nominal harus sama persis DAN nama pihak hutang muncul pada counterparty / judul / isi.
     */
    fun matchOpenDebt(
        debts: List<Debt>,
        kind: TxKind,
        amount: Long,
        counterparty: String?,
        title: String?,
        text: String?,
    ): Debt? {
        val expectedDirection = when (kind) {
            TxKind.Income -> DebtDirection.Lent
            TxKind.Expense -> DebtDirection.Borrowed
            TxKind.Transfer -> return null
        }
        val haystack = listOfNotNull(counterparty, title, text)
            .joinToString(" ")
            .lowercase()
            .replace(NON_ALNUM, " ")
            .replace(SPACES, " ")
            .trim()
        if (haystack.isEmpty()) return null

        val partyNorm = normalizeMerchantKey(counterparty)
        val candidates = debts.filter { d ->
            if (d.settled || d.direction != expectedDirection || d.amount != amount) return@filter false
            val name = normalizeMerchantKey(d.counterparty) ?: return@filter false
            if (partyNorm != null && (partyNorm == name || partyNorm.contains(name) || name.contains(partyNorm))) return@filter true
            // Cek kecocokan utuh per batas kata di judul/isi notifikasi.
            Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(haystack)
        }
        if (candidates.isEmpty()) return null
        // Utamakan kecocokan nama paling panjang/spesifik, lalu jatuh tempo paling dekat.
        return candidates.sortedWith(
            compareByDescending<Debt> { normalizeMerchantKey(it.counterparty)?.length ?: 0 }
                .thenBy { it.dueDate ?: "9999-12-31" },
        ).first()
    }
}
