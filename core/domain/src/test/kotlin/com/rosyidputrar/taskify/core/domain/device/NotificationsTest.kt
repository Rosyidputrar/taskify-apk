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
