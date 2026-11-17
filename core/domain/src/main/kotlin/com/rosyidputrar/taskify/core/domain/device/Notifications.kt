package com.rosyidputrar.taskify.core.domain.device

import com.rosyidputrar.taskify.core.domain.finance.TxKind

/**
 * Logika modul Notifikasi yang tidak bergantung pada Android: kategori, sensor kode OTP, deteksi
 * transaksi bank/e-wallet, status paket, dan petunjuk "ini bisa jadi tugas".
 */

enum class NotifCategory(val label: String) {
    Chat("Chat"),
    Money("Bank & dompet"),
    Shopping("Belanja & kurir"),
    Email("Email"),
    Other("Lainnya"),
}

/** Seberapa banyak isi notifikasi yang disimpan. */
enum class NotifStorageLevel(val label: String, val hint: String) {
    Full("Lengkap", "Judul dan isi disimpan, bisa dicari dan ditindaklanjuti."),
    TitleOnly("Judul saja", "Hanya judul (mis. nama pengirim). Draf transaksi tetap dibuat."),
    CountOnly("Hitungan saja", "Hanya jumlah per aplikasi, tanpa teks apa pun."),
}

object NotifApps {
    val CHAT = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "jp.naver.line.android", "com.facebook.orca", "com.facebook.mlite", "com.discord", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.slack", "com.microsoft.teams", "us.zoom.videomeetings", "com.instagram.barcelona",
        "com.snapchat.android", "com.viber.voip", "org.thoughtcrime.securesms", "com.google.android.apps.dynamite",
    )

    /** Bank & dompet digital populer di Indonesia → nama pendek. */
    val MONEY = mapOf(
        "com.bca" to "BCA", "com.bca.mybca.omni.android" to "myBCA", "com.bca.blu" to "blu", "id.co.bankbkemobile.digitalbank" to "SeaBank",
        "id.bmri.livin" to "Livin' Mandiri", "id.co.bri.brimo" to "BRImo", "src.com.bni" to "BNI", "id.co.bni.mobile" to "BNI",
        "com.bsm.activity2" to "BSI", "co.id.bankbsi.superapp" to "BSI", "com.jago.digitalBanking" to "Jago", "com.btpn.dc" to "Jenius",
        "id.co.cimbniaga.mobile.android" to "OCTO", "com.ocbcnisp.onemobileapp" to "ONe Mobile", "com.permatabank.mobile" to "PermataMobile",
        "com.gojek.app" to "GoPay", "com.gojek.gopay" to "GoPay", "ovo.id" to "OVO", "id.dana" to "DANA", "com.shopeepay.id" to "ShopeePay",
        "com.telkom.mwallet" to "LinkAja", "com.alloapp.yump" to "Allo Bank", "id.co.neobank.mobile" to "Neo+", "com.bankjateng.digibank" to "Bank Jateng",
    )

    val SHOPPING = setOf(
        "com.shopee.id", "com.tokopedia.tkpd", "com.lazada.android", "blibli.mobile.commerce", "com.bukalapak.android",
        "com.zhiliaoapp.musically.go", "com.jet.jnt", "id.co.sicepat.consumer", "com.anteraja.aca", "com.jne.android",
        "com.grabtaxi.passenger", "id.co.posindonesia.mobile", "com.ninjavan.app", "com.alfagift.app", "com.indomaret.klikindomaret",
    )

    val EMAIL = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "ch.protonmail.android",
        "com.samsung.android.email.provider", "com.google.android.gm.lite",
    )

    fun moneyName(pkg: String): String? = MONEY[pkg]
}

object NotifClassifier {
    fun category(pkg: String, androidCategory: String?): NotifCategory = when {
        pkg in NotifApps.MONEY -> NotifCategory.Money
        pkg in NotifApps.SHOPPING -> NotifCategory.Shopping
        pkg in NotifApps.EMAIL || androidCategory == "email" -> NotifCategory.Email
        pkg in NotifApps.CHAT || androidCategory == "msg" -> NotifCategory.Chat
        else -> NotifCategory.Other
    }
}

/** Sensor kode sekali pakai: angka 4–8 digit di dekat kata kode/OTP tidak pernah disimpan. */
object OtpRedactor {
    private val CONTEXT = Regex("\\b(otp|kode|code|token|verifikasi|verification|pin|passcode|sandi sekali)\\b", RegexOption.IGNORE_CASE)
    private val DIGITS = Regex("(?<!\\d)(?<!\\d[.,])\\d{4,8}(?!\\d)(?![.,]\\d)")
    private val SPACED = Regex("(?<!\\d)\\d{3}[ -]\\d{3}(?!\\d)")

    fun redact(text: String?): String? {
        if (text.isNullOrEmpty() || !CONTEXT.containsMatchIn(text)) return text
        return SPACED.replace(DIGITS.replace(text) { "•".repeat(it.value.length) }) { "••• •••" }
    }
}

/** Hasil deteksi transaksi dari notifikasi bank/e-wallet. */
data class MoneyNotif(val amount: Long, val kind: TxKind, val counterparty: String?, val source: String)

object MoneyNotifParser {
    private val AMOUNT = Regex("(?:rp|idr)\\.?\\s?([\\d]{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,](\\d{2}))?(?![\\d])", RegexOption.IGNORE_CASE)
    private val PROMO = Regex(
        "promo|cashback hingga|diskon|voucher|dapatkan|hingga rp|s\\.d\\.?|s/d|gratis|kupon|undian|jatuh tempo|tagihan .* akan|segera bayar|minimal|limit|penawaran|pinjaman hingga|cicilan 0",
        RegexOption.IGNORE_CASE,
    )
    private val FAILED = Regex("gagal|dibatalkan|ditolak|tidak berhasil|failed|pending", RegexOption.IGNORE_CASE)
    private val INCOME = Regex(
        "dana masuk|uang masuk|transfer masuk|menerima|diterima dari|kamu terima|anda terima|telah masuk|\\bmasuk\\b|kredit|\\bcr\\b|refund|pengembalian dana|cashback (sebesar|rp)|top ?up berhasil|isi saldo berhasil|saldo bertambah",
        RegexOption.IGNORE_CASE,
    )
    private val EXPENSE = Regex(
        "pembayaran|bayar|dibayar|transfer ke|kirim (uang|dana)|berhasil dikirim|pembelian|\\bdebit\\b|\\bdb\\b|tarik tunai|penarikan|uang keluar|dana keluar|\\bkeluar\\b|qris|belanja|telah didebet|autodebet|potong saldo",
        RegexOption.IGNORE_CASE,
    )
    /** "Transaksi berhasil ... dari X": uang masuk dari X walau tanpa kata "diterima". */
    private val INCOME_FROM = Regex(
        "(?:berhasil|sukses)\\b.{0,80}?\\bdari\\s+(?!rekening\\b|akun\\b|saldo\\b|dompet\\b)[A-Za-z]",
        RegexOption.IGNORE_CASE,
    )
    private val SUCCESS = Regex("berhasil|sukses|telah|sudah|diterima|masuk|success|transaksi|terdebet|didebet|dikredit", RegexOption.IGNORE_CASE)
    private val PARTY = Regex("\\b(?:ke|kepada|dari|di|merchant|penerima|pengirim)\\s*:?\\s+([A-Za-z0-9&'.\\- ]{2,40}?)(?=\\s*(?:[,.;:!]|\\s(?:sebesar|senilai|rp|idr|pada|tgl|tanggal|jam|via|dengan|pakai|menggunakan|melalui|lewat|untuk|berhasil|telah|sudah)\\b|$))", RegexOption.IGNORE_CASE)

    /**
     * null = bukan transaksi (promo, OTP, gagal, tagihan yang belum dibayar, tanpa nominal).
     * Hanya dipakai untuk aplikasi kategori bank & dompet ([NotifApps.MONEY]).
     */
    fun parse(pkg: String, title: String?, text: String?): MoneyNotif? {
        val source = NotifApps.moneyName(pkg) ?: return null
        val all = listOfNotNull(title, text).joinToString(" · ").replace('\n', ' ')
        if (all.isBlank() || PROMO.containsMatchIn(all) || FAILED.containsMatchIn(all)) return null
        val m = AMOUNT.find(all) ?: return null
        val amount = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()?.takeIf { it > 0 } ?: return null
        // "dari X" hanya dihitung sebagai sinyal masuk bila sesudah frasa sukses (INCOME_FROM),
        // supaya "Transfer ... dari BCA/rekening ke Y" tidak salah dibaca sebagai uang masuk.
        // Frasa "transaksi berhasil" saja bukan arah: tanpa sinyal arah, lebih baik tidak ada
        // draf daripada draf berarah salah.
        val income = listOfNotNull(INCOME.find(all), INCOME_FROM.find(all)).minByOrNull { it.range.first }
        val expense = EXPENSE.find(all)
        val kind = when {
            income != null && expense != null -> if (income.range.first < expense.range.first) TxKind.Income else TxKind.Expense
            income != null -> TxKind.Income
            expense != null -> TxKind.Expense
            else -> return null
        }
        if (!SUCCESS.containsMatchIn(all)) return null
        val party = PARTY.findAll(all).map { it.groupValues[1].trim() }
            .firstOrNull { p -> p.isNotEmpty() && !p.lowercase().startsWith("rp") && p.any(Char::isLetter) && p.lowercase() !in setOf("rekening", "akun", "anda", "kamu", "saldo") }
        return MoneyNotif(amount, kind, party?.let(::tidy), source)
    }

    private fun tidy(s: String) = s.trim().trimEnd('.', '-').split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
        if (w.length <= 3 && w.all(Char::isUpperCase)) w else w.lowercase().replaceFirstChar(Char::uppercase)
    }
}

enum class ParcelStage(val label: String, val rank: Int) {
    Packed("Dikemas", 1),
    Shipped("Dikirim", 2),
    OutForDelivery("Sedang diantar", 3),
    Delivered("Tiba", 4),
}

data class ParcelNotif(val stage: ParcelStage, val reference: String?)

object ParcelParser {
    private val REF = Regex("(?:resi|no\\.? ?resi|pesanan|order|no\\.? pesanan|awb)\\s*(?:#|no\\.?|:)?\\s*([A-Z0-9]{8,24})", RegexOption.IGNORE_CASE)
    private val STAGES = listOf(
        ParcelStage.Delivered to Regex("telah diterima|sudah diterima|paket (telah|sudah) (sampai|tiba)|terkirim|delivered|berhasil dikirim ke|pesanan selesai|sudah sampai", RegexOption.IGNORE_CASE),
        ParcelStage.OutForDelivery to Regex("sedang diantar|dalam pengantaran|kurir (menuju|sedang|akan)|out for delivery|sedang dalam perjalanan ke alamat", RegexOption.IGNORE_CASE),
        ParcelStage.Shipped to Regex("telah dikirim|sudah dikirim|dalam pengiriman|diserahkan ke kurir|shipped|sedang dikirim|paket dikirim", RegexOption.IGNORE_CASE),
        ParcelStage.Packed to Regex("sedang dikemas|dikemas|diproses penjual|siap dikirim|menunggu pickup|menunggu diambil kurir", RegexOption.IGNORE_CASE),
    )
    private val PROMO = Regex("promo|diskon|voucher|flash sale|gratis ongkir|cashback", RegexOption.IGNORE_CASE)

    fun parse(pkg: String, title: String?, text: String?): ParcelNotif? {
        if (pkg !in NotifApps.SHOPPING) return null
        val all = listOfNotNull(title, text).joinToString(" · ")
        if (all.isBlank() || PROMO.containsMatchIn(all)) return null
        val stage = STAGES.firstOrNull { it.second.containsMatchIn(all) }?.first ?: return null
        return ParcelNotif(stage, REF.find(all)?.groupValues?.get(1)?.uppercase())
    }
}

/** Notifikasi yang kemungkinan perlu ditindaklanjuti (chip "Tugas?"). */
object TaskHint {
    private val WORDS = Regex(
        "\\b(besok|lusa|nanti|deadline|tenggat|tolong|mohon|jangan lupa|ingatkan|ingetin|rapat|meeting|kirim(kan)?|bayar|kumpul(kan)?|siapkan|segera|sebelum|jam \\d{1,2}|pukul \\d{1,2})\\b",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeTask(category: NotifCategory, text: String?): Boolean =
        (category == NotifCategory.Chat || category == NotifCategory.Email || category == NotifCategory.Other) &&
            !text.isNullOrBlank() && WORDS.containsMatchIn(text)

    /** Judul tugas dari notifikasi: "Balas: Budi — kirim file revisi". */
    fun taskTitle(category: NotifCategory, title: String?, text: String?): String {
        val body = text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val head = title?.trim().orEmpty()
        val base = when {
            category == NotifCategory.Chat && head.isNotEmpty() && body.isNotEmpty() -> "Balas: $head — $body"
            head.isNotEmpty() && body.isNotEmpty() -> "$head — $body"
            else -> head.ifEmpty { body }
        }
        return if (base.length <= 120) base else base.take(119).trimEnd() + "…"
    }
}
