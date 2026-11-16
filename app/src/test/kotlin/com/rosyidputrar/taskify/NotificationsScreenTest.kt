package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.device.NotifSettings
import com.rosyidputrar.taskify.core.domain.device.MoneyNotif
import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.ParcelNotif
import com.rosyidputrar.taskify.core.domain.device.ParcelStage
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppCount
import com.rosyidputrar.taskify.core.store.device.DraftState
import com.rosyidputrar.taskify.core.store.device.NotifEntry
import com.rosyidputrar.taskify.core.store.device.Parcel
import com.rosyidputrar.taskify.feature.notifications.DraftEdit
import com.rosyidputrar.taskify.feature.notifications.NotifAccess
import com.rosyidputrar.taskify.feature.notifications.NotifFilter
import com.rosyidputrar.taskify.feature.notifications.NotificationsActions
import com.rosyidputrar.taskify.feature.notifications.NotificationsContent
import com.rosyidputrar.taskify.feature.notifications.NotificationsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Modul Notifikasi: izin, lini waktu, saringan, grup percakapan, draf transaksi, paket. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class NotificationsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val day = "2026-11-16"
    private fun at(h: Int, m: Int) = Dates.localDateTimeToMillis(day, "%02d:%02d".format(h, m))

    private fun e(
        id: String, pkg: String, app: String, title: String?, text: String?, h: Int, m: Int,
        cat: NotifCategory = NotifCategory.Other, conv: String? = null, money: MoneyNotif? = null, parcel: ParcelNotif? = null,
    ) = NotifEntry(id, pkg, app, "k$id", conv, title, text, cat, at(h, m), day, money = money, draft = money?.let { DraftState.Pending }, parcel = parcel)

    private val entries = listOf(
        e("1", "com.whatsapp", "WhatsApp", "Budi", "Tolong kirim file revisinya besok pagi ya", 14, 32, NotifCategory.Chat, conv = "Tim Kantor"),
        e("2", "com.whatsapp", "WhatsApp", "Sari", "Siap, aku cek dulu", 14, 20, NotifCategory.Chat, conv = "Tim Kantor"),
        e("3", "com.whatsapp", "WhatsApp", "Andi", "Oke 👍", 14, 5, NotifCategory.Chat, conv = "Tim Kantor"),
        e("4", "com.bca", "BCA", "Transaksi Berhasil", "Pembayaran QRIS Rp35.000 ke KOPI KENANGAN berhasil", 12, 15, NotifCategory.Money, money = MoneyNotif(35_000, TxKind.Expense, "Kopi Kenangan", "BCA")),
        e("5", "com.shopee.id", "Shopee", "Pesanan dikirim", "Pesanan #SPXID0123456789 telah dikirim", 9, 40, NotifCategory.Shopping, parcel = ParcelNotif(ParcelStage.Shipped, "SPXID0123456789")),
        e("6", "com.google.android.gm", "Gmail", "Undangan rapat", "Rapat sprint Kamis jam 10", 8, 2, NotifCategory.Email),
    )

    private val wallets = listOf(Wallet("w1", "BCA", WalletType.Bank, 0, null, null, 0.0, false, balance = 1_250_000))

    private fun state(access: Boolean = true) = NotificationsUiState(
        today = day,
        day = day,
        access = NotifAccess(granted = access, connected = true, restrictedHint = true),
        settings = NotifSettings(),
        entries = entries,
        total = 143,
        previousTotal = 128,
        appCounts = listOf(AppCount("com.whatsapp", "WhatsApp", 58), AppCount("com.instagram.android", "Instagram", 21), AppCount("com.shopee.id", "Shopee", 9)),
        drafts = entries.filter { it.money != null },
        wallets = wallets,
        parcels = listOf(Parcel("SPXID0123456789", "com.shopee.id", "Shopee", ParcelStage.Shipped, at(9, 40), "Pesanan dikirim")),
        loaded = true,
    )

    private val verticalList = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    }

    @Test fun onboardingExplainsPrivacyAndSteps() {
        show { NotificationsContent(state(access = false), NotificationsActions.None, padding) }
        compose.onNodeWithText("Izinkan setelan terbatas").assertExists()
        compose.onNodeWithText("Buka Akses notifikasi").assertExists()
        compose.onNodeWithText("Lengkap").assertExists()
        compose.onRoot().captureRoboImage("screenshots/notifications_onboarding_light.png")
    }

    @Test fun timelineScreenshotAndSummary() {
        show(dark = true) { NotificationsContent(state(), NotificationsActions.None, padding) }
        compose.onNodeWithText("1 transaksi menunggu konfirmasi").assertExists()
        compose.onNodeWithText("↑ 12% dari kemarin").assertExists()
        compose.onRoot().captureRoboImage("screenshots/notifications_timeline_dark.png")
        compose.onNode(verticalList).performScrollToNode(hasText("Tim Kantor · 3 pesan"))
        compose.onNodeWithText("Tim Kantor · 3 pesan").assertExists()
    }

    @Test fun filtersGroupsAndSwipeActions() {
        var s by mutableStateOf(state())
        val calls = mutableListOf<String>()
        val actions = object : NotificationsActions {
            override fun setFilter(filter: NotifFilter) { s = s.copy(filter = filter) }
            override fun makeTask(entry: NotifEntry) { calls += "task:${entry.id}" }
            override fun hide(entry: NotifEntry) { calls += "hide:${entry.id}" }
        }
        show { NotificationsContent(s, actions, padding) }
        val list = compose.onNode(verticalList)
        // Grup percakapan bisa dibuka; tiap pesan punya aksi aksesibilitas pengganti geser.
        list.performScrollToNode(hasText("Tim Kantor · 3 pesan"))
        compose.onNodeWithText("Tim Kantor · 3 pesan").performClick()
        val hasTaskAction = SemanticsMatcher("aksi Jadikan tugas") { n ->
            n.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Jadikan tugas" } == true
        }
        val row = compose.onNode(hasText("Tolong kirim file revisinya besok pagi ya") and hasTaskAction)
        row.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Jadikan tugas" }.action()
        assertEquals(listOf("task:1"), calls)
        // Saringan "Perlu ditindak": chat "besok pagi", undangan "rapat … jam 10" (Tugas?), dan draf transaksi.
        list.performScrollToNode(hasText("Perlu ditindak"))
        compose.onNodeWithText("Perlu ditindak").performClick()
        assertEquals(NotifFilter.Actionable, s.filter)
        assertEquals(setOf("1", "4", "6"), s.visible.map { it.id }.toSet())
    }

    @Test fun draftsSheetSavesWithChosenWallet() {
        var saved: DraftEdit? = null
        val actions = object : NotificationsActions {
            override fun saveDraft(entry: NotifEntry, edit: DraftEdit) { saved = edit }
        }
        show { NotificationsContent(state(), actions, padding, openDrafts = true) }
        compose.onNodeWithText("Tinjau sebelum dicatat").assertExists()
        compose.onNodeWithText("Simpan").assertExists()
        compose.onNodeWithText("BCA", useUnmergedTree = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/notifications_drafts_light.png")
        // Dompet belum dipetakan → pilih dulu (chip "BCA"), baru Simpan aktif.
        compose.onNode(hasText("BCA") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
        compose.onNodeWithText("Simpan").performClick()
        assertEquals(DraftEdit(35_000, TxKind.Expense, "Kopi Kenangan", "w1", null), saved)
    }

    @Test fun emptyDayMessage() {
        show { NotificationsContent(state().copy(entries = emptyList(), total = 0, previousTotal = null, drafts = emptyList(), parcels = emptyList()), NotificationsActions.None, padding) }
        compose.onNode(verticalList).performScrollToNode(hasText("Belum ada notifikasi"))
        compose.onNodeWithText("Belum ada notifikasi").assertExists()
        // Grafik per jam tetap punya teks alternatif saat kosong.
        compose.onNodeWithContentDescription("Belum ada notifikasi").assertExists()
    }
}
