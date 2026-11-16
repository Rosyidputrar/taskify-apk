package com.rosyidputrar.taskify.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.reminders.ContextReminders
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Penjadwal pengingat kontekstual (WorkManager — tetap jalan walau Taskify tidak dibuka). */
class ContextReminderScheduler(private val context: Context, private val prefs: ContextReminderPrefs) {
    /** Dipanggil saat aplikasi mulai dan setiap saklar/jam diubah. */
    fun schedule() {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ContextCheckWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS).build(),
        )
        // Pemeriksaan segera (mis. tagihan hari ini) tanpa menunggu periode pertama.
        wm.enqueueUniqueWork(WORK_CHECK_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ContextCheckWorker>().build())
        scheduleJournal()
    }

    fun scheduleJournal(nowMillis: Long = System.currentTimeMillis()) {
        val wm = WorkManager.getInstance(context)
        if (!prefs.journal) {
            wm.cancelUniqueWork(WORK_JOURNAL)
            return
        }
        wm.enqueueUniqueWork(
            WORK_JOURNAL,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<JournalNudgeWorker>()
                .setInitialDelay(ContextReminders.delayUntil(prefs.journalTime, nowMillis), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /**
     * Dipanggil JournalNudgeWorker setelah bekerja: jadwalkan nudge besok — kecuali sudah keluar
     * ([dbOwner] null). Tanpa penjaga ini pekerjaan periodik menjadwalkan ulang dirinya sendiri
     * selamanya walau tidak ada akun yang masuk.
     */
    fun rescheduleJournalAfterNudge(dbOwner: String?, nowMillis: Long = System.currentTimeMillis()) {
        if (dbOwner == null) return
        scheduleJournal(nowMillis + 60_000L)
    }

    companion object {
        const val WORK_CHECK = "context-check"
        const val WORK_CHECK_NOW = "context-check-now"
        const val WORK_JOURNAL = "journal-nudge"
    }
}

/**
 * Pemeriksaan berkala: catat otomatis seri auto_post yang jatuh tempo (dulu hanya selama aplikasi
 * terbuka), beri tahu tagihan manual yang jatuh tempo (Catat / Lewati), dan anggaran yang naik
 * ke level peringatan / terlampaui. Setiap kejadian diberitahukan sekali.
 */
class ContextCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val devicePrefs: DevicePrefs by inject()
    private val finance: FinanceRepository by inject()
    private val prefs: ContextReminderPrefs by inject()
    private val notifier: ContextNotifier by inject()

    override suspend fun doWork(): Result {
        if (devicePrefs.dbOwner == null) return Result.success() // belum/tidak masuk akun
        val today = Dates.today()
        // Baca-ubah-tulis `notified` harus atomik: worker periodik 6 jam dan one-time
        // "context-check-now" bisa berjalan bersamaan dan saling menghilangkan kunci.
        val notified = prefs.updateNotified { ContextReminders.pruneKeys(it, today) }
        runCatching { notifier.autoPosted(finance.processDueRecurring(today)) }
        val newKeys = mutableSetOf<String>()
        if (prefs.bills) {
            val due = ContextReminders.billsToNotify(finance.watchRecurring().first(), today, notified)
            due.forEach { notifier.bill(it, today) }
            newKeys += due.map(ContextReminders::billKey)
        }
        if (prefs.budgets) {
            val month = Dates.monthStart(today)
            val last = Dates.addDays(Dates.parse(month).plusMonths(1).toString(), -1L)
            val alerts = ContextReminders.budgetAlerts(finance.watchBudgets(month, last).first(), month, notified)
            alerts.forEach(notifier::budget)
            newKeys += alerts.map { ContextReminders.budgetKey(it, month, it.level) }
        }
        if (newKeys.isNotEmpty()) prefs.updateNotified { it + newKeys }
        return Result.success()
    }
}

/** Satu pengingat malam bila streak jurnal akan putus; menjadwalkan dirinya lagi untuk besok. */
class JournalNudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val devicePrefs: DevicePrefs by inject()
    private val home: HomeRepository by inject()
    private val prefs: ContextReminderPrefs by inject()
    private val notifier: ContextNotifier by inject()
    private val scheduler: ContextReminderScheduler by inject()

    override suspend fun doWork(): Result {
        try {
            if (devicePrefs.dbOwner != null && prefs.journal) {
                val today = Dates.today()
                val streakToday = home.watchJournalStreak(today).first()
                val streakYesterday = home.watchJournalStreak(Dates.addDays(today, -1L)).first()
                val wroteToday = streakToday > streakYesterday
                if (ContextReminders.shouldNudgeJournal(streakYesterday, wroteToday)) notifier.journal(streakYesterday)
            }
        } finally {
            // +1 menit supaya tidak terjadwal ke jam yang sama hari ini lagi. Bila sudah keluar
            // (dbOwner null) JANGAN jadwalkan ulang: pekerjaan ini harus mati bersama sesi.
            scheduler.rescheduleJournalAfterNudge(devicePrefs.dbOwner)
        }
        return Result.success()
    }
}

/** Tombol "Catat" / "Lewati" di notifikasi tagihan. */
class BillActionReceiver : BroadcastReceiver(), KoinComponent {
    private val finance: FinanceRepository by inject()
    private val notifier: ContextNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_RECURRING_ID) ?: return
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                notifier.cancelBill(id)
                val r = finance.watchRecurring().first().firstOrNull { it.id == id } ?: return@launch
                when (action) {
                    ACTION_POST -> finance.postOccurrence(r)
                    ACTION_SKIP -> finance.skipOccurrence(r)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("Taskify", "Aksi tagihan gagal", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_POST = "com.rosyidputrar.taskify.bill.POST"
        const val ACTION_SKIP = "com.rosyidputrar.taskify.bill.SKIP"
        const val EXTRA_RECURRING_ID = "recurring_id"
    }
}
