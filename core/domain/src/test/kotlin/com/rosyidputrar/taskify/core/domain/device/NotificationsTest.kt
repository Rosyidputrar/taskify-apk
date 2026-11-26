package com.rosyidputrar.taskify.core.domain.device

import com.rosyidputrar.taskify.core.domain.finance.TxKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationsTest {
    @Test fun categories() {
        assertEquals(NotifCategory.Chat, NotifClassifier.category("com.whatsapp", null))
        assertEquals(NotifCategory.Money, NotifClassifier.category("id.dana", "msg"))
        assertEquals(NotifCategory.Shopping, NotifClassifier.category("com.shopee.id", null))
        assertEquals(NotifCategory.Email, NotifClassifier.category("com.example.mail", "email"))
        assertEquals(NotifCategory.Chat, NotifClassifier.category("com.example.chat", "msg"))
        assertEquals(NotifCategory.Other, NotifClassifier.category("com.example", null))
    }

    @Test fun otpIsRedacted() {
        assertEquals("Kode OTP kamu ••••••. Jangan berikan ke siapa pun.", OtpRedactor.redact("Kode OTP kamu 482913. Jangan berikan ke siapa pun."))
        assertEquals("Your verification code is ••• •••", OtpRedactor.redact("Your verification code is 123 456"))
        // Tanpa konteks kode: angka biasa dibiarkan.
        assertEquals("Pesanan 12345678 dikirim", OtpRedactor.redact("Pesanan 12345678 dikirim"))
        // Nominal tidak tersentuh.
        assertEquals("Kode promo HEMAT, transfer Rp50.000", OtpRedactor.redact("Kode promo HEMAT, transfer Rp50.000"))
        assertNull(OtpRedactor.redact(null))
    }

    private fun money(pkg: String, title: String?, text: String?) = MoneyNotifParser.parse(pkg, title, text)

    @Test fun bankAndWalletTransactions() {
        assertEquals(
            MoneyNotif(50_000, TxKind.Expense, "Budi Santoso", "BCA"),
            money("com.bca", "Transaksi Berhasil", "Transfer ke BUDI SANTOSO sebesar Rp50.000 berhasil."),
        )
        assertEquals(
            MoneyNotif(1_250_000, TxKind.Income, "PT Maju Jaya", "Livin' Mandiri"),
            money("id.bmri.livin", "Dana Masuk", "Kamu menerima Rp 1.250.000,00 dari PT MAJU JAYA"),
        )
        assertEquals(
            MoneyNotif(25_000, TxKind.Expense, "Kopi Kenangan", "GoPay"),
            money("com.gojek.app", "Pembayaran berhasil", "Pembayaran Rp25.000 di Kopi Kenangan pakai GoPay berhasil"),
        )
        assertEquals(TxKind.Income, money("id.dana", "Saldo DANA bertambah", "Top up berhasil sebesar Rp100.000")?.kind)
        assertEquals(TxKind.Expense, money("id.co.bri.brimo", "BRImo", "Pembayaran QRIS Rp 15.500 ke WARUNG BU SRI telah berhasil")?.kind)
        assertEquals(75_000L, money("ovo.id", "OVO", "Transaksi berhasil. IDR 75,000.00 telah didebet")?.amount)
    }

    @Test fun myBcaFinancialNotes() {
        // Format "Catatan Finansial" myBCA: arah dari kata Pemasukan/Pengeluaran, tanpa kata "berhasil".
        assertEquals(
            MoneyNotif(21_896_100, TxKind.Expense, null, "myBCA"),
            money("com.bca.mybca.omni.android", "Catatan Finansial", "Pengeluaran sebesar IDR 21,896,100.00 di kategori Belanja."),
        )
        assertEquals(
            MoneyNotif(22_096_000, TxKind.Income, null, "myBCA"),
            money("com.bca.mybca.omni.android", "Catatan Finansial", "Pemasukan sebesar IDR 22,096,000.00 dari **LNA **RI ***ATI di kategori Transfer Rekening."),
        )
    }

    @Test fun wondrReceivedTransaction() {
        // Format wondr by BNI: "Kamu baru aja terima Rp..." = uang masuk.
        assertEquals(
            MoneyNotif(21_421_000, TxKind.Income, null, "wondr by BNI"),
            money("id.bni.wondr", "Transaksi diterima!", "Kamu baru aja terima Rp21.421.000. Klik untuk cek detailnya."),
        )
        // "Transaksi Berhasil" generik tanpa kata arah tetap tidak dibuatkan draf.
        assertNull(money("id.bni.wondr", "Transaksi Berhasil!", "Kamu baru aja transaksi sebesar Rp21.238.100. Klik untuk cek detailnya."))
    }

    @Test fun extendedDirectionPatterns() {
        // diterima + nominal tanpa "dari" = income.
        assertEquals(TxKind.Income, money("com.bca", "Dana Diterima", "Dana diterima Rp250.000")?.kind)
        assertEquals(TxKind.Income, money("id.bmri.livin", "Bonus", "Bonus sebesar Rp500.000 telah masuk")?.kind)
        assertEquals(TxKind.Income, money("id.dana", "DANA", "Dana cair sebesar Rp1.000.000")?.kind)
        // QRIS diterima merchant = income, bukan expense.
        assertEquals(TxKind.Income, money("id.dana", "QRIS", "Pembayaran QRIS diterima Rp75.000")?.kind)
        // transfer ... ke (tidak berdempetan) = expense, pihak lawan terbaca.
        assertEquals(
            MoneyNotif(50_000, TxKind.Expense, "Budi", "BCA"),
            money("com.bca", "Transfer Berhasil", "Transfer berhasil Rp50.000 ke BUDI"),
        )
        assertEquals(TxKind.Expense, money("com.bca", "Info", "Dana ditransfer sebesar Rp100.000")?.kind)
        assertEquals(TxKind.Expense, money("ovo.id", "OVO", "Saldo berkurang Rp15.000 untuk pembayaran")?.kind)
        assertEquals(TxKind.Expense, money("com.bca", "Biaya Admin", "Biaya admin Rp6.500 telah terpotong")?.kind)
        assertEquals(TxKind.Expense, money("id.co.bri.brimo", "Mutasi", "Mutasi DB Rp200.000")?.kind)
        // Nominal singkatan.
        assertEquals(500_000L, money("id.dana", "DANA", "Top up berhasil Rp500rb")?.amount)
        assertEquals(1_500_000L, money("com.bca", "Transfer Masuk", "Transfer masuk Rp1,5 juta dari BUDI")?.amount)
        // Pihak lawan format a/n.
        assertEquals("Budi Santoso", money("com.bca", "Transfer Berhasil", "Transfer berhasil Rp50.000 ke rekening a/n BUDI SANTOSO")?.counterparty)
        // SMS bank dari aplikasi pesan.
        assertEquals(
            MoneyNotif(100_000, TxKind.Income, null, "BCA"),
            money("com.google.android.apps.messaging", "BCA", "Dana masuk Rp100.000 ke rekening Anda"),
        )
    }

    @Test fun qrisWithoutRpPrefixAndUntukMerchant() {
        assertEquals(
            MoneyNotif(13_500, TxKind.Expense, "MyTelkomsel Apps", "wondr by BNI"),
            MoneyNotifParser.parse(
                "id.bni.wondr",
                "Pembayaran QRIS berhasil",
                "Pembayaran QRIS untuk MyTelkomsel Apps sebesar 13.500 telah berhasil.",
            ),
        )
        assertEquals(
            MoneyNotif(25_000, TxKind.Expense, "Kopi Kenangan", "ShopeePay"),
            MoneyNotifParser.parse(
                "com.shopee.id",
                "Pembayaran ShopeePay berhasil",
                "Pembayaran ShopeePay untuk Kopi Kenangan sebesar Rp25.000 berhasil.",
            ),
        )
    }

    @Test fun payrollPayoutEnglishAndExtendedBanks() {
        assertEquals(
            MoneyNotif(5_000_000, TxKind.Income, "PT Nusantara", "Livin' Mandiri"),
            money("id.bmri.livin", "Payroll Masuk", "Payroll dari PT NUSANTARA sebesar Rp5.000.000"),
        )
        assertEquals(
            MoneyNotif(1_200_000, TxKind.Income, "PT Maju", "Bank Saqu"),
            MoneyNotifParser.parse("id.banksaqu.app", "Payout", "Pay out dari PT MAJU sebesar 1.200.000 selesai"),
        )
        assertEquals(
            MoneyNotif(750_000, TxKind.Income, null, "AstraPay"),
            MoneyNotifParser.parse("com.astrapay.customer", "Pencairan Dana", "Pencairan dana sebesar Rp750.000 berhasil"),
        )
        assertEquals(
            MoneyNotif(3_500_000, TxKind.Income, "Acme Corp", "HSBC Indonesia"),
            MoneyNotifParser.parse("id.co.hsbc.hsbcindonesia", "Incoming Transfer", "You received IDR 3,500,000 from Acme Corp"),
        )
        // Bank BPD / Swasta / Syariah / E-Wallet dengan package ID baru tetap terdeteksi lewat appLabel.
        assertEquals(
            MoneyNotif(250_000, TxKind.Income, "Dinas", "Bank Kaltimtara"),
            MoneyNotifParser.parse("id.co.bankaltimtara.v2", "Gaji", "Gaji dari Dinas sebesar Rp250.000 telah masuk", appLabel = "Bank Kaltimtara"),
        )
        assertEquals(
            MoneyNotif(45_000, TxKind.Expense, "Indomaret", "i.saku"),
            MoneyNotifParser.parse("com.indomarco.isaku.new", "Pembayaran Berhasil", "Pembayaran di Indomaret sebesar 45.000 berhasil", appLabel = "i.saku"),
        )
        assertEquals(
            NotifCategory.Money,
            NotifClassifier.category("id.co.banknagari.new", null, appLabel = "Bank Nagari"),
        )
        // Chat dengan nama kontak/label yang mengandung kata "Bank" tetap bukan Money.
        assertEquals(NotifCategory.Chat, NotifClassifier.category("com.whatsapp", null, appLabel = "Bank"))
        assertNull(MoneyNotifParser.parse("com.whatsapp", "Payroll", "Gaji masuk Rp5.000.000", appLabel = "Bank"))
    }

    @Test fun incomeFromPartyAfterSuccessPhrase() {
        // "Transaksi berhasil ... dari X" = uang masuk, walau tanpa kata "diterima".
        assertEquals(
            MoneyNotif(1_000_000, TxKind.Income, "Budi", "BCA"),
            money("com.bca", "Transaksi Berhasil", "Transaksi berhasil Rp1.000.000 dari BUDI"),
        )
        // Tanpa arah sama sekali: lebih baik tidak ada draf daripada draf berarah salah.
        assertNull(money("com.bca", "Transaksi Berhasil", "Transaksi berhasil Rp50.000"))
        // "dari rekening" = rekening sumber, bukan pengirim: ini uang keluar ke Budi, bukan masuk.
        assertEquals(
            MoneyNotif(100_000, TxKind.Expense, "Budi", "BCA"),
            money("com.bca", "Transfer", "Transfer Rp100.000 dari rekening BCA ke Budi berhasil"),
        )
    }

    @Test fun ignoresPromosFailuresBillsAndOtherApps() {
        assertNull(money("com.gojek.app", "Promo!", "Cashback hingga Rp50.000 untuk pembayaran pakai GoPay"))
        assertNull(money("com.bca", "Transaksi gagal", "Transfer Rp50.000 ke BUDI gagal diproses"))
        assertNull(money("id.dana", "Tagihan", "Tagihan listrik Rp350.000 jatuh tempo besok, segera bayar"))
        assertNull(money("com.bca", "Info", "Kode OTP 123456 untuk transaksi Anda"))
        assertNull(money("com.whatsapp", "Budi", "Transfer ke aku Rp50.000 berhasil ya"))
        assertNull(money("com.bca", "Saldo", "Saldo rekening Anda saat ini"))
    }

    @Test fun parcels() {
        assertEquals(ParcelNotif(ParcelStage.Shipped, "SPXID0123456789"), ParcelParser.parse("com.shopee.id", "Pesanan dikirim", "Pesanan #SPXID0123456789 telah dikirim"))
        assertEquals(ParcelStage.OutForDelivery, ParcelParser.parse("com.jet.jnt", "J&T", "Paket kamu sedang diantar kurir hari ini")?.stage)
        assertEquals(ParcelStage.Delivered, ParcelParser.parse("com.tokopedia.tkpd", "Pesanan tiba", "Paket telah diterima oleh Rosyid")?.stage)
        assertNull(ParcelParser.parse("com.shopee.id", "Flash sale!", "Gratis ongkir untuk paket dikirim hari ini"))
        assertNull(ParcelParser.parse("com.whatsapp", "Budi", "paketnya sudah dikirim"))
    }

    @Test fun taskHints() {
        assertTrue(TaskHint.looksLikeTask(NotifCategory.Chat, "Tolong kirim file revisinya besok pagi ya"))
        assertFalse(TaskHint.looksLikeTask(NotifCategory.Chat, "Haha mantap"))
        assertFalse(TaskHint.looksLikeTask(NotifCategory.Money, "Bayar besok"))
        assertEquals("Balas: Budi — kirim file revisi", TaskHint.taskTitle(NotifCategory.Chat, "Budi", "kirim file revisi\nmakasih"))
        assertEquals("Rapat jam 3", TaskHint.taskTitle(NotifCategory.Other, null, "Rapat jam 3"))
        assertEquals(120, TaskHint.taskTitle(NotifCategory.Other, "x".repeat(200), null).length)
    }
}
