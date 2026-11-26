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

    /** Bank (BUMN, Swasta, Syariah, BPD, Asing/Campuran) & dompet digital di Indonesia → nama pendek. */
    val MONEY = mapOf(
        // Bank BUMN
        "id.bmri.livin" to "Livin' Mandiri",
        "id.co.bri.brimo" to "BRImo",
        "id.co.bri.cermo" to "BRI",
        "src.com.bni" to "BNI",
        "id.co.bni.mobile" to "BNI",
        "id.bni.wondr" to "wondr by BNI",
        "id.co.btn.mobilebanking.android" to "BTN",
        "id.co.btn.bale" to "balé by BTN",
        "com.bsm.activity2" to "BSI",
        "co.id.bankbsi.superapp" to "BSI",

        // Bank Swasta Nasional & Bank Digital
        "com.bca" to "BCA",
        "com.bca.mybca.omni.android" to "myBCA",
        "com.bca.blu" to "blu",
        "id.co.cimbniaga.mobile.android" to "OCTO",
        "id.co.danamon.dbankpro" to "D-Bank PRO",
        "com.danamon.dbankpro" to "D-Bank PRO",
        "id.co.danamon.dbank" to "Danamon",
        "com.permatabank.mobile" to "PermataMobile",
        "net.myinfosys.PermataMobileX" to "Permata ME",
        "com.bankmega.msmile" to "M-Smile",
        "id.co.bankmega.msmile" to "M-Smile",
        "com.panin.mobilebanking" to "Bank Panin",
        "com.ocbcnisp.onemobileapp" to "ONe Mobile",
        "com.uob.id.tmrw" to "UOB TMRW",
        "com.uob.mightyindonesia" to "UOB",
        "id.com.uiux.mobile" to "M2U ID",
        "com.maybank.m2uid" to "M2U ID",
        "com.banksinarmas.simobiplus" to "SimobiPlus",
        "id.co.sinarmas.simobiplus" to "SimobiPlus",
        "com.btpn.dc" to "Jenius",
        "id.co.agi.mobile" to "Artha Graha",
        "id.co.bankmestika.mobile" to "Bank Mestika",
        "com.bankmayapada.myonline" to "Bank Mayapada",
        "id.co.kbbank.kbstar" to "KB Bank",
        "com.bukopin.wokee" to "KB Bank",
        "com.jago.digitalBanking" to "Jago",
        "id.co.neobank.mobile" to "Neo+",
        "com.amarbank.digital" to "Amar Bank",
        "com.banksampoerna.mobile" to "Bank Sampoerna",
        "id.co.nobubank.nobuneo" to "Nobu Bank",
        "id.co.bankvictoria.mobile" to "Bank Victoria",
        "id.co.bankganesha.bangga" to "Bank Ganesha",
        "id.co.bankina.bina" to "Bank Ina",
        "id.co.bankbba.mobile" to "Bank Bumi Arta",
        "id.co.bankcapital.mobile" to "Bank Capital",
        "id.co.bankmaspion.mobile" to "Bank Maspion",
        "id.co.okbank.mobile" to "Bank Oke",
        "id.krom.bank" to "Krom",
        "com.krom.bank" to "Krom",
        "id.co.bankraya.apps" to "Bank Raya",
        "id.co.bankbkemobile.digitalbank" to "SeaBank",
        "com.alloapp.yump" to "Allo Bank",
        "id.banksaqu.app" to "Bank Saqu",
        "id.co.hibank.hi" to "Hibank",
        "id.co.superbank.app" to "Superbank",
        "com.superbank.app" to "Superbank",

        // Bank Syariah
        "com.muamalat.din" to "Bank Muamalat",
        "id.co.bankmuamalat.din" to "Bank Muamalat",
        "id.co.megasyariah.msyariah" to "Bank Mega Syariah",
        "id.co.pdsb.mobile" to "Panin Dubai Syariah",
        "id.co.bcasyariah.mobile" to "BCA Syariah",
        "id.co.aladin.bank" to "Bank Aladin Syariah",
        "id.co.btpnsyariah.tepat" to "BTPN Syariah",
        "id.co.nanobank.aira" to "Bank Nano Syariah",
        "id.co.bankaceh.action" to "Bank Aceh Syariah",
        "id.co.bankntbsyariah.mobile" to "Bank NTB Syariah",

        // Bank Pembangunan Daerah (BPD)
        "com.bankdki.jakone" to "JakOne Mobile",
        "id.co.bankdki.jakone" to "JakOne Mobile",
        "id.co.bankbjb.digi" to "Bank BJB",
        "com.bjb.digi" to "Bank BJB",
        "com.bankjateng.digibank" to "Bank Jateng",
        "id.co.bankjateng.bima" to "Bank Jateng",
        "com.bankjatim.jconnect" to "Bank Jatim",
        "id.co.bpddiy.mobile" to "Bank DIY",
        "id.co.bpdbali.mobile" to "Bank BPD Bali",
        "id.co.banksumut.mobile" to "Bank BPD Sumut",
        "id.co.banknagari.ollin" to "Bank Nagari",
        "id.co.bsb.mobile" to "Bank Sumsel Babel",
        "id.co.banklampung.mobile" to "Bank Lampung",
        "id.co.bankjambi.mobile" to "Bank Jambi",
        "id.co.bankbengkulu.mobile" to "Bank Bengkulu",
        "id.co.bankkalbar.mobile" to "Bank Kalbar",
        "id.co.bankkalsel.aksel" to "Bank Kalsel",
        "id.co.bankaltimtara.dg" to "Bank Kaltimtara",
        "id.co.bankkalteng.betang" to "Bank Kalteng",
        "id.co.banksulselbar.mobile" to "Bank Sulselbar",
        "id.co.banksultra.mobile" to "Bank Sultra",
        "id.co.banksulutgo.bsgtouch" to "Bank SulutGo",
        "id.co.banksulteng.mobile" to "Bank Sulteng",
        "id.co.bankmalukumalut.mobile" to "Bank Maluku Malut",
        "id.co.bankntt.bpung" to "Bank NTT",
        "id.co.bankpapua.mobile" to "Bank Papua",
        "id.co.bankbanten.jawara" to "Bank Banten",
        "id.co.brksyariah.mobile" to "Bank Riau Kepri Syariah",

        // Bank Asing & Campuran
        "id.co.hsbc.hsbcindonesia" to "HSBC Indonesia",
        "com.scb.id.mobile" to "Standard Chartered",
        "com.citi.mobile.id" to "Citibank",
        "com.dbs.id.digitalbank" to "DBS Indonesia",
        "com.icbc.im.id" to "ICBC Indonesia",
        "id.co.ccbi.mobile" to "CCB Indonesia",
        "id.co.bankwoorisaudara.mobile" to "Bank Woori Saudara",
        "com.shinhan.global.id.bank" to "Bank Shinhan",
        "id.co.hanabank.linebank" to "LINE Bank",
        "com.hanabank.ebk.channel.android.hananbank.id" to "KEB Hana",
        "id.co.ctbcbank.mobile" to "CTBC Indonesia",
        "id.co.qnb.mobile" to "QNB Indonesia",

        // E-Wallet & Pembayaran Digital Indonesia
        "id.dana" to "DANA",
        "com.gojek.app" to "GoPay",
        "com.gojek.gopay" to "GoPay",
        "ovo.id" to "OVO",
        "com.shopeepay.id" to "ShopeePay",
        "com.telkom.mwallet" to "LinkAja",
        "com.isaku.app" to "i.saku",
        "com.bca.sakuku" to "Sakuku",
        "com.doku.wallet" to "DOKU",
        "com.astrapay.customer" to "AstraPay",
        "id.co.motionpay" to "MotionPay",
        "com.mnc.motionpay" to "MotionPay",
        "com.paytren.connect" to "Paytren",
        "com.paytren.emoney" to "Paytren",
        "id.co.netzme" to "Netzme",
        "com.netzme.id" to "Netzme",
        "id.co.kaspro" to "KasPro",
        "com.digiasia.kaspro" to "KasPro",
        "com.speedcashus" to "SpeedCash",
        "com.bimasakti.speedcash" to "SpeedCash",
        "com.truemoney.id" to "TrueMoney",
        "id.co.truemoney.wallet" to "TrueMoney",
        "com.posindonesia.giropos" to "PosPay",
        "id.co.paydia.app" to "Paydia",
        "com.payfazz.android" to "Fazz",
        "id.fazz.agen" to "Fazz",
        "com.pede.emoney" to "OttoCash",
        "com.xl.xltunai" to "XL Tunai",
        "com.uangku.app" to "Uangku",
        "id.co.ezeelink.app" to "Ezeelink",
        "com.gudangvoucher.app" to "GudangVoucher",
        "id.flip" to "Flip",
    )

    /**
     * Pencocokan cadangan berdasarkan nama aplikasi (dari PackageManager) agar bank/e-wallet
     * yang mengganti package ID atau belum terdaftar eksplisit tetap terdeteksi.
     */
    private val MONEY_LABEL_PATTERN = Regex(
        "\\b(?:" +
            "bank|bpd|m-?banking|mobile banking|digibank|" +
            "livin|brimo|wondr|bal[eé]|byond|mybca|m-?bca|blu\\s+by\\s+bca|" +
            "octo|d-?bank|permata|m-?smile|panin|ocbc|tmrw|uob|m2u|maybank|simobi|sinarmas|" +
            "jenius|btpn|smbc|artha graha|mestika|mayapada|kbstar|bukopin|jago|neobank|" +
            "amar|sampoerna|nobu|victoria|ganesha|bumi arta|maspion|ok\\s*bank|krom|raya|" +
            "seabank|allo\\s*bank|saqu|hibank|superbank|" +
            "muamalat|m-?syariah|bsya|aladin|nano syariah|aira|" +
            "jakone|digi\\s*bjb|bima mobile|jconnect|ollin|bsb mobile|aksel|kaltimtara|betang|bsgtouch|" +
            "hsbc|standard chartered|citibank|citi mobile|dbs|icbc|mufg|mizuho|resona|woori|shinhan|keb hana|line bank|myhana|deutsche|jpmorgan|anz|ctbc|qnb|" +
            "dana|gopay|ovo|shopeepay|linkaja|i\\.?saku|sakuku|doku|astrapay|motionpay|paytren|netzme|kaspro|speedcash|truemoney|pospay|paydia|fazz|ottocash|xl tunai|uangku|ezeelink|gudangvoucher|flip" +
            ")\\b",
        RegexOption.IGNORE_CASE,
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

    fun moneyName(pkg: String, appLabel: String? = null): String? {
        MONEY[pkg]?.let { return it }
        if (pkg in CHAT || pkg in EMAIL || pkg in SHOPPING) return null
        val label = appLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (MONEY_LABEL_PATTERN.containsMatchIn(label)) label else null
    }
}

object NotifClassifier {
    fun category(pkg: String, androidCategory: String?, appLabel: String? = null): NotifCategory = when {
        pkg in NotifApps.MONEY -> NotifCategory.Money
        pkg in NotifApps.SHOPPING -> NotifCategory.Shopping
        pkg in NotifApps.EMAIL || androidCategory == "email" -> NotifCategory.Email
        pkg in NotifApps.CHAT || androidCategory == "msg" -> NotifCategory.Chat
        NotifApps.moneyName(pkg, appLabel) != null -> NotifCategory.Money
        else -> NotifCategory.Other
    }
}

/** Sensor kode sekali pakai: angka 4–8 digit di dekat kata kode/OTP tidak pernah disimpan. */
object OtpRedactor {
    private val CONTEXT = Regex("\\b(otp|kode|code|token|verifikasi|verification|pin|passcode|sandi sekali)\\b", RegexOption.IGNORE_CASE)
    private val DIGITS = Regex("(?<!\\d)(?<!\\d[.,])\\d{4,8}(?!\\d)(?![.,]\\d)")
    private val SPACED = Regex("(?<!\\d)\\d{3}[ -]\\d{3}(?!\\d)")

    fun hasContext(text: String?): Boolean = !text.isNullOrEmpty() && CONTEXT.containsMatchIn(text)

    fun redact(text: String?): String? = redact(text, forceContext = false)

    fun redact(text: String?, forceContext: Boolean): String? {
        if (text.isNullOrEmpty() || (!forceContext && !CONTEXT.containsMatchIn(text))) return text
        return SPACED.replace(DIGITS.replace(text) { "•".repeat(it.value.length) }) { "••• •••" }
    }
}

/** Hasil deteksi transaksi dari notifikasi bank/e-wallet. */
data class MoneyNotif(val amount: Long, val kind: TxKind, val counterparty: String?, val source: String)

object MoneyNotifParser {
    private val AMOUNT = Regex("(?:rp|idr)\\.?\\s?([\\d]{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,](\\d{2}))?(?![\\d])", RegexOption.IGNORE_CASE)
    /** Cadangan bila bank/dompet menulis nominal tanpa "Rp"/"IDR", mis. "sebesar 13.500 telah berhasil". */
    private val CONTEXT_AMOUNT = Regex(
        "\\b(?:sebesar|senilai|nominal|sejumlah|jumlah|total|amount)\\s*:?\\s*(?:rp\\.?\\s*|idr\\.?\\s*)?([\\d]{1,3}(?:[.,]\\d{3})+|[1-9]\\d{3,})(?:[.,](\\d{2}))?(?![\\d:/-])",
        RegexOption.IGNORE_CASE,
    )
    private val PROMO = Regex(
        "promo|cashback hingga|diskon|voucher|dapatkan|hingga rp|s\\.d\\.?|s/d|gratis(?!\\s*(?:biaya|admin|transfer))|kupon|undian|jatuh tempo|tagihan .* akan|segera bayar|minimal|limit|penawaran|pinjaman hingga|cicilan 0",
        RegexOption.IGNORE_CASE,
    )
    private val FAILED = Regex("gagal|dibatalkan|ditolak|tidak berhasil|failed|unsuccessful|declined|rejected|cancell?ed|pending|kadaluarsa|kedaluwarsa|expired", RegexOption.IGNORE_CASE)
    private val INCOME = Regex(
        "\\bpemasukan\\b|dana masuk|uang masuk|transfer masuk|menerima|diterima dari|diterima\\s+(?:sebesar|senilai|rp|idr|\\d)|kamu terima|anda terima|terima (?:dana|uang|transfer|pembayaran|bayaran)|terima\\s+(?:rp|idr|\\d)|telah masuk|\\bmasuk\\b|dikredit(?:kan)?|mutasi kredit|kredit|\\bcr\\b|refund|pengembalian dana|cashback (?:sebesar|rp|idr)|top ?up (?:berhasil|sebesar|rp|idr|\\d)|isi saldo (?:berhasil|sebesar|rp|idr|\\d)|saldo bertambah|\\bpay ?roll\\b|\\bpay ?out\\b|\\bgaji\\b|\\bsalary\\b|\\bupah\\b|\\binsentif\\b|\\bthr\\b|pencairan(?: dana)?|dicairkan|(?:dana|uang|pinjaman)\\s+cair|\\bcair\\s+(?:sebesar|rp|idr|\\d)|\\bbonus\\b|\\bkomisi\\b|\\bdividen\\b|\\bbunga\\b|\\bhadiah\\b|hasil penjualan|penjualan\\s+(?:sebesar|rp|idr)|reimburse(?:ment)?|klaim\\s+(?:sebesar|rp|idr|berhasil)|\\bdisbursement\\b|setoran(?: tunai)?|setor tunai|\\bdeposited\\b|incoming transfer|transfer received|payment received|funds? received|money received|you received|\\breceived\\s+(?:rp|idr|\\d)|\\bcredited\\b",
        RegexOption.IGNORE_CASE,
    )
    private val EXPENSE = Regex(
        "\\bpengeluaran\\b|pembayaran|bayar|dibayar|transfer ke|transfer keluar|transfer\\b.{0,60}?\\bke\\b|ditransfer|terkirim|kirim (?:uang|dana)|kirim\\s+(?:rp|idr|\\d|sebesar)|berhasil dikirim|pembelian|\\bdebit\\b|\\bdb\\b|mutasi debet|tarik tunai|penarikan|uang keluar|dana keluar|\\bkeluar\\b|saldo berkurang|berkurang\\s+(?:sebesar|rp|idr)|terpotong|dipotong|potongan|biaya admin(?:istrasi)?|angsuran|cicilan|iuran|denda|\\bpajak\\b|bunga (?:pinjaman|kredit|angsuran)|qris|belanja|telah didebet|terdebet|didebet|didebit|terdebit|auto ?deb[ei]t|potong saldo|\\bpaid\\b|payment (?:to|at|for|of)|\\bsent\\s+(?:rp|idr|\\d)|\\bdebited\\b|\\bpurchase\\b|\\bwithdraw(?:al|n)?\\b|outgoing transfer|transfer to",
        RegexOption.IGNORE_CASE,
    )
    /** QRIS/pembayaran yang diterima merchant = uang masuk, walau ada kata "pembayaran"/"qris". */
    private val RECEIVED_PAYMENT = Regex(
        "(?:pembayaran\\s+)?qris\\s+diterima|pembayaran\\s+diterima|diterima\\s+(?:via|melalui)\\s+qris",
        RegexOption.IGNORE_CASE,
    )
    /** "Transaksi berhasil ... dari X": uang masuk dari X walau tanpa kata "diterima". */
    private val INCOME_FROM = Regex(
        "(?:berhasil|sukses|success|completed)\\b.{0,80}?\\b(?:dari|from)\\s+(?!rekening\\b|akun\\b|saldo\\b|dompet\\b|account\\b)[A-Za-z]",
        RegexOption.IGNORE_CASE,
    )
    private val SUCCESS = Regex(
        "berhasil|sukses|telah|sudah|diterima|masuk|mutasi|(?:pemasukan|pengeluaran)\\s+sebesar|success(?:ful)?|completed|settled|selesai|transaksi|terdebet|didebet|didebit|terdebit|dikredit(?:kan)?|kredit|credited|debited|received|paid|sent|dicairkan|cair|ditransfer|terkirim|terpotong|dipotong|berkurang|\\bpay ?roll\\b|\\bpay ?out\\b",
        RegexOption.IGNORE_CASE,
    )
    private val PARTY = Regex(
        "\\b(?:ke|kepada|dari|di|untuk|merchant|penerima|pengirim|to|from|at|a/n|atas nama)\\s*:?\\s+([A-Za-z0-9&'.\\- ]{2,40}?)(?=\\s*(?:[,.;:!]|\\s(?:sebesar|senilai|nominal|sejumlah|rp|idr|pada|tgl|tanggal|jam|via|dengan|pakai|menggunakan|melalui|lewat|untuk|ke|dari|berhasil|telah|sudah|successfully|success|completed)\\b|$))",
        RegexOption.IGNORE_CASE,
    )
    private val IGNORED_PARTIES = setOf(
        "rekening", "akun", "anda", "kamu", "saldo", "transaksi", "pembayaran", "pembelian", "qris", "tagihan", "nominal", "account", "your", "kategori", "cek", "klik",
    )

    /** Nominal singkatan: "Rp500rb", "Rp50k", "Rp1,5 juta". */
    private val SUFFIX_AMOUNT = Regex(
        "(?:rp|idr)\\.?\\s?(\\d+(?:[.,]\\d+)?)\\s*(rb|ribu|k|jt|juta)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
    )
    private val SMS_BANK_SENDERS = listOf(
        "BCA" to "BCA", "BNI" to "BNI", "BRI" to "BRI", "MANDIRI" to "Mandiri", "DANA" to "DANA",
        "OVO" to "OVO", "GOPAY" to "GoPay", "SHOPEEPAY" to "ShopeePay", "BSI" to "BSI", "BTN" to "BTN",
    )

    private fun smsBankSource(pkg: String, title: String?): String? {
        if (pkg !in SMS_PACKAGES) return null
        val sender = title?.uppercase() ?: return null
        return SMS_BANK_SENDERS.firstOrNull { (key, _) -> sender.contains(key) }?.second
    }

    private fun suffixAmount(all: String): Long? {
        val m = SUFFIX_AMOUNT.find(all) ?: return null
        val number = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        val multiplier = when (m.groupValues[2].lowercase()) {
            "jt", "juta" -> 1_000_000.0
            else -> 1_000.0
        }
        return (number * multiplier).toLong().takeIf { it > 0 }
    }

    /**
     * null = bukan transaksi (promo, OTP, gagal, tagihan yang belum dibayar, tanpa nominal).
     * Hanya dipakai untuk aplikasi kategori bank & dompet ([NotifApps.MONEY] / [NotifApps.moneyName]),
     * serta notifikasi ShopeePay dari aplikasi Shopee.
     */
    fun parse(pkg: String, title: String?, text: String?, appLabel: String? = null): MoneyNotif? {
        val all = listOfNotNull(title, text).joinToString(" · ").replace('\n', ' ')
        if (all.isBlank()) return null
        val source = NotifApps.moneyName(pkg, appLabel)
            ?: if (pkg == "com.shopee.id" && all.contains("shopeepay", ignoreCase = true)) "ShopeePay" else smsBankSource(pkg, title) ?: return null
        if (PROMO.containsMatchIn(all) || FAILED.containsMatchIn(all)) return null
        val amount = suffixAmount(all) ?: run {
            val m = AMOUNT.find(all) ?: CONTEXT_AMOUNT.find(all) ?: return null
            m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()?.takeIf { it > 0 } ?: return null
        }
        // "dari X" hanya dihitung sebagai sinyal masuk bila sesudah frasa sukses (INCOME_FROM),
        // supaya "Transfer ... dari BCA/rekening ke Y" tidak salah dibaca sebagai uang masuk.
        // Frasa "transaksi berhasil" saja bukan arah: tanpa sinyal arah, lebih baik tidak ada
        // draf daripada draf berarah salah.
        val income = listOfNotNull(INCOME.find(all), INCOME_FROM.find(all)).minByOrNull { it.range.first }
        val expense = EXPENSE.find(all)
        val kind = when {
            RECEIVED_PAYMENT.containsMatchIn(all) -> TxKind.Income
            income != null && expense != null -> when {
                income.range.first < expense.range.first -> TxKind.Income
                expense.range.first < income.range.first -> TxKind.Expense
                income.value.contains("masuk", ignoreCase = true) || income.value.contains("diterima", ignoreCase = true) -> TxKind.Income
                else -> TxKind.Expense
            }
            income != null -> TxKind.Income
            expense != null -> TxKind.Expense
            else -> return null
        }
        if (!SUCCESS.containsMatchIn(all)) return null
        val party = PARTY.findAll(all).map { it.groupValues[1].trim() }
            .firstOrNull { p ->
                val firstWord = p.lowercase().split(' ').firstOrNull()
                p.isNotEmpty() && !p.lowercase().startsWith("rp") && p.any(Char::isLetter) && firstWord !in IGNORED_PARTIES
            }
        return MoneyNotif(amount, kind, party?.let(::tidy), source)
    }

    private fun tidy(s: String) = s.trim().trimEnd('.', '-').split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
        when {
            w.length <= 3 && w.all(Char::isUpperCase) -> w
            w.any(Char::isUpperCase) && w.any(Char::isLowerCase) -> w
            else -> w.lowercase().replaceFirstChar(Char::uppercase)
        }
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
