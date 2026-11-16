package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.device.AppDay
import com.rosyidputrar.taskify.core.domain.device.DeviceDay
import com.rosyidputrar.taskify.core.domain.device.MoneyNotif
import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.ParcelNotif
import com.rosyidputrar.taskify.core.domain.device.ParcelStage
import com.rosyidputrar.taskify.core.domain.device.UsageSession
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.store.device.AppCount
import com.rosyidputrar.taskify.core.store.device.DraftState
import com.rosyidputrar.taskify.core.store.device.NewNotif
import com.rosyidputrar.taskify.core.store.device.NotifAction
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceRepositoriesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val notifs = NotificationLogRepository(db)
    private val usage = UsageRepository(db)
    private val day = "2026-11-16"

    @After fun tearDown() = runBlocking<Unit> {
        db.close()
        scope.cancel()
    }

    private fun n(pkg: String, title: String, text: String, at: Long, key: String = "k", category: NotifCategory = NotifCategory.Chat, d: String = day) =
        NewNotif(pkg, pkg.substringAfterLast('.'), key, null, title, text, category, at, d)

    @Test fun logDedupesAndCounts() = runBlocking<Unit> {
        assertNotNull(notifs.log(n("com.whatsapp", "Budi", "halo", 1_000)))
        assertNull(notifs.log(n("com.whatsapp", "Budi", "halo", 2_000)), "pembaruan tanpa isi baru = duplikat")
        assertNotNull(notifs.log(n("com.whatsapp", "Budi", "lagi?", 3_000)))
        assertNotNull(notifs.log(n("com.whatsapp", "Budi", "halo", 1_000 + NotificationLogRepository.DEDUPE_MS + 1)))
        notifs.count(day, "com.instagram.android", "Instagram")
        assertEquals(3, notifs.watchDay(day).first().size)
        assertEquals(mapOf(day to 4), notifs.watchDailyTotals(day, day).first())
        assertEquals(listOf(AppCount("com.whatsapp", "whatsapp", 3), AppCount("com.instagram.android", "Instagram", 1)), notifs.watchAppCounts(day, day).first())
        assertTrue(notifs.watchTimes(day, "com.whatsapp").first().isNotEmpty())
    }

    @Test fun draftsHiddenAndHandled() = runBlocking<Unit> {
        val id = notifs.log(n("com.bca", "Transaksi", "Transfer Rp50.000", 1_000, category = NotifCategory.Money).copy(money = MoneyNotif(50_000, TxKind.Expense, "Budi", "BCA")))!!
        val drafts = notifs.watchDrafts().first()
        assertEquals(MoneyNotif(50_000, TxKind.Expense, "Budi", "BCA"), drafts.single().money)
        assertEquals(DraftState.Pending, drafts.single().draft)
        assertEquals(1, notifs.watchDraftCount().first())
        notifs.setDraft(id, DraftState.Saved)
        notifs.markHandled(id, NotifAction.Transaction)
        assertEquals(0, notifs.watchDraftCount().first())
        assertEquals(NotifAction.Transaction, notifs.get(id)!!.handled)
        notifs.setHidden(id, true)
        assertTrue(notifs.watchDay(day).first().isEmpty())
        // Tersembunyi tidak ikut dicari.
        assertTrue(notifs.search("Transfer").isEmpty())
    }

    @Test fun parcelsKeepLatestStagePerReference() = runBlocking<Unit> {
        notifs.log(n("com.shopee.id", "Pesanan", "dikemas", 1_000, key = "a", category = NotifCategory.Shopping).copy(parcel = ParcelNotif(ParcelStage.Packed, "SPX1")))
        notifs.log(n("com.shopee.id", "Pesanan", "dikirim", 2_000, key = "b", category = NotifCategory.Shopping).copy(parcel = ParcelNotif(ParcelStage.Shipped, "SPX1")))
        notifs.log(n("com.jet.jnt", "J&T", "tiba", 3_000, key = "c", category = NotifCategory.Shopping).copy(parcel = ParcelNotif(ParcelStage.Delivered, "JX9")))
        val parcels = notifs.watchParcels(0).first()
        assertEquals(listOf("SPX1" to ParcelStage.Shipped, "JX9" to ParcelStage.Delivered), parcels.map { it.ref to it.stage })
    }

    @Test fun pruneKeepsPendingDraftsAndCounts() = runBlocking<Unit> {
        notifs.log(n("com.whatsapp", "Lama", "x", 1_000, d = "2026-11-01"))
        notifs.log(n("com.bca", "T", "Rp1", 2_000, d = "2026-11-01", category = NotifCategory.Money).copy(money = MoneyNotif(1, TxKind.Expense, null, "BCA")))
        notifs.prune(beforeDay = "2026-11-09", countsBeforeDay = "2025-10-01")
        assertTrue(notifs.watchDay("2026-11-01").first().map { it.title } == listOf("T"))
        assertEquals(mapOf("2026-11-01" to 2), notifs.watchDailyTotals("2026-11-01", "2026-11-01").first())
        notifs.clearAll()
        assertTrue(notifs.watchDays().first().isEmpty())
    }

    @Test fun usageReplaceDayIsIdempotent() = runBlocking<Unit> {
        val sessions = listOf(UsageSession("a", 0, 60_000), UsageSession("b", 60_000, 180_000))
        val apps = listOf(AppDay("b", 120_000, 1, 120_000), AppDay("a", 60_000, 1, 60_000))
        repeat(2) { usage.replaceDay(day, sessions, apps, mapOf("a" to "Aplikasi A"), DeviceDay(day, 180_000, 5, 42)) }
        assertEquals(listOf(DeviceDay(day, 180_000, 5, 42)), usage.watchDevice(day, day).first())
        assertEquals(listOf("b", "a"), usage.watchApps(day, day).first().map { it.pkg })
        assertEquals("Aplikasi A", usage.watchApps(day, day).first().last().label)
        assertEquals(sessions, usage.watchSessions(day).first())
        assertEquals(listOf(UsageSession("a", 0, 60_000)), usage.watchSessions(day, "a").first())
        assertEquals(day, usage.lastDay())
        usage.replaceDay("2026-11-17", emptyList(), listOf(AppDay("a", 30_000, 2, 20_000)), emptyMap(), DeviceDay("2026-11-17", 30_000, 1, null))
        assertEquals(90_000L, usage.watchApps(day, "2026-11-17").first().first { it.pkg == "a" }.foregroundMs)
        assertEquals(2, usage.watchAppHistory("a", day, "2026-11-17").first().size)
        assertEquals(setOf(day, "2026-11-17"), usage.watchAppDays(day, "2026-11-17").first().keys)
        usage.pruneSessions("2026-11-17")
        assertTrue(usage.watchSessions(day).first().isEmpty())
        assertEquals(2, usage.watchDevice(day, "2026-11-17").first().size, "rekap tetap ada")
    }

    @Test fun deviceTablesNeverUpload() = runBlocking<Unit> {
        notifs.log(n("com.whatsapp", "Budi", "halo", 1_000))
        usage.replaceDay(day, emptyList(), emptyList(), emptyMap(), DeviceDay(day, 0, 0, null))
        assertNull(db.getNextCrudTransaction())
    }
}
