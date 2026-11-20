package com.rosyidputrar.taskify.core.device

import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.NotifStorageLevel
import com.rosyidputrar.taskify.core.domain.device.UsageEvent
import com.rosyidputrar.taskify.core.domain.device.UsageEvent.Kind
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeviceCaptureTest {
    private val own = "com.rosyidputrar.taskify"
    private val t = 1_763_280_000_000L // 2025-11-16 15.00 WIB

    private fun raw(pkg: String = "com.whatsapp", title: String? = "Budi", text: String? = "Kode OTP 482913 jangan dibagikan", conversation: String? = null, ongoing: Boolean = false, summary: Boolean = false, cat: String? = null) =
        RawNotif(pkg, "k", title, conversation, text, cat, t, ongoing, summary)

    @Test fun filtersOwnSystemExcludedOngoingAndSummaries() {
        val s = NotifSettings(excluded = setOf("com.instagram.android"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(pkg = own), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(pkg = "com.android.systemui"), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(pkg = "com.instagram.android"), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(ongoing = true), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(cat = "progress"), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(summary = true), s, own, "x"))
        assertEquals(CaptureDecision.Ignore, NotificationCapture.decide(raw(title = " ", text = null), s, own, "x"))
        // Notifikasi berjalan boleh dicatat bila pengguna mematikan saringannya.
        assertIs<CaptureDecision.Log>(NotificationCapture.decide(raw(ongoing = true), s.copy(ignoreOngoing = false), own, "x"))
    }

    @Test fun conversationIsRedactedAndCapped() {
        val long = "Grup Verifikasi " + "x".repeat(300)
        val d = NotificationCapture.decide(
            raw(conversation = "Kode OTP 482913 jangan dibagikan", title = "Budi", text = "halo"),
            NotifSettings(), own, "WhatsApp",
        ) as CaptureDecision.Log
        assertEquals("Kode OTP •••••• jangan dibagikan", d.notif.conversation)
        val capped = NotificationCapture.decide(
            raw(conversation = long, title = "Budi", text = "halo"),
            NotifSettings(), own, "WhatsApp",
        ) as CaptureDecision.Log
        assertEquals(200, capped.notif.conversation?.length)
        // TitleOnly: percakapan (isi) tidak disimpan, seperti teks.
        val titleOnly = NotificationCapture.decide(
            raw(conversation = "rahasia", title = "Budi", text = "halo"),
            NotifSettings(level = NotifStorageLevel.TitleOnly), own, "WhatsApp",
        ) as CaptureDecision.Log
        assertNull(titleOnly.notif.conversation)
    }

    @Test fun storageLevelsAndOtp() {
        val full = NotificationCapture.decide(raw(), NotifSettings(), own, "WhatsApp") as CaptureDecision.Log
        assertEquals("Kode OTP •••••• jangan dibagikan", full.notif.text)
        assertEquals(NotifCategory.Chat, full.notif.category)
        assertEquals("2025-11-16", full.notif.day)
        val title = NotificationCapture.decide(raw(), NotifSettings(level = NotifStorageLevel.TitleOnly), own, "WhatsApp") as CaptureDecision.Log
        assertNull(title.notif.text)
        assertEquals("Budi", title.notif.title)
        assertEquals(CaptureDecision.CountOnly("2025-11-16", "com.whatsapp"), NotificationCapture.decide(raw(), NotifSettings(level = NotifStorageLevel.CountOnly), own, "WhatsApp"))
    }

    @Test fun moneyDraftSurvivesTitleOnly() {
        val r = raw(pkg = "com.bca", title = "Transaksi Berhasil", text = "Transfer ke BUDI sebesar Rp50.000 berhasil.")
        val d = NotificationCapture.decide(r, NotifSettings(level = NotifStorageLevel.TitleOnly), own, "BCA") as CaptureDecision.Log
        assertEquals(50_000L, d.notif.money?.amount)
        assertEquals(TxKind.Expense, d.notif.money?.kind)
        assertNull(d.notif.text)
    }

    private val day = "2026-11-16"
    private val w = UsageMath.window(day)
    private fun at(h: Int, m: Int = 0) = w.first + h * UsageMath.HOUR + m * 60_000L

    @Test fun planComputesEachDayAndKeepsOlderStoredDays() {
        val events = listOf(
            UsageEvent("a", Kind.Resumed, at(-1, 30)), // kemarin 23.30, melewati tengah malam
            UsageEvent(null, Kind.Unlock, at(7)),
            UsageEvent("a", Kind.Paused, at(0, 30)),
            UsageEvent("launcher", Kind.Resumed, at(8)),
        )
        val yesterday = "2026-11-15"
        val plans = UsageCollector.plan(events, now = at(9), firstDay = yesterday, today = day, dayStartHour = 0, lastStored = null, ignore = setOf("launcher"))
        assertEquals(listOf(yesterday, day), plans.map { it.day })
        assertEquals(30 * 60_000L, plans[0].device.screenMs)
        assertEquals(30 * 60_000L, plans[1].device.screenMs)
        assertEquals(1, plans[1].device.unlocks)
        assertEquals(at(7), plans[1].device.firstPickup)
        // Kemarin sudah tersimpan dan kejadian sistem baru mulai 23.30 → jangan timpa.
        val again = UsageCollector.plan(events, now = at(9), firstDay = yesterday, today = day, dayStartHour = 0, lastStored = day, ignore = setOf("launcher"))
        assertEquals(listOf(day), again.map { it.day })
        assertEquals(emptyList(), UsageCollector.plan(emptyList(), at(9), yesterday, day, 0, null, emptySet()))
    }
}
