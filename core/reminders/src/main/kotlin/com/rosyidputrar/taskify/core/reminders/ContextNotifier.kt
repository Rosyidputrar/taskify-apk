package com.rosyidputrar.taskify.core.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * Notifikasi pengingat kontekstual, masing-masing di kanalnya sendiri supaya bisa diatur terpisah
 * dari Pengaturan sistem. "Sembunyikan isi notifikasi" ikut dihormati (judul & nominal disembunyikan).
 */
class ContextNotifier(private val context: Context, private val prefs: ReminderPrefs) {
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_BILLS, "Tagihan jatuh tempo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Transaksi berulang yang menunggu dicatat, dan yang dicatat otomatis"
                },
                NotificationChannel(CHANNEL_BUDGETS, "Anggaran", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Anggaran mencapai batas peringatan atau terlampaui"
                },
                NotificationChannel(CHANNEL_JOURNAL, "Pengingat jurnal", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Satu pengingat malam bila streak jurnal akan putus"
                },
            ),
        )
    }

    private fun canPost() = com.rosyidputrar.taskify.core.reminders.NotificationGate.allowed(context)

    fun bill(r: Recurring, today: String) {
        if (!canPost()) return
        ensureChannels()
        val hide = prefs.hideContent
        val verb = if (r.kind == TxKind.Income) "Pemasukan" else "Tagihan"
        val whenText = r.nextRunOn?.let { Dates.relativeDayLabel(it, today).lowercase() } ?: "hari ini"
        val n = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (hide) "Ada transaksi berulang jatuh tempo" else "$verb ${r.title.ifBlank { "berulang" }} · ${Rupiah.format(r.amount)}")
            .setContentText(if (hide) null else "Jatuh tempo $whenText. Catat sekarang atau lewati kejadian ini.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openModule("Finance"))
            .addAction(0, "Catat", billAction(r.id, BillActionReceiver.ACTION_POST))
            .addAction(0, "Lewati", billAction(r.id, BillActionReceiver.ACTION_SKIP))
            .build()
        notify(billNotificationId(r.id), n)
    }

    fun autoPosted(count: Int) {
        if (count <= 0 || !canPost()) return
        ensureChannels()
        val n = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("$count transaksi berulang dicatat otomatis")
            .setContentText("Cek di Keuangan › Transaksi.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openModule("Finance"))
            .build()
        notify(ID_AUTO_POSTED, n)
    }

    fun budget(b: BudgetStatus) {
        if (!canPost()) return
        ensureChannels()
        val hide = prefs.hideContent
        val pct = if (b.amount > 0) (b.spent * 100 / b.amount) else 0
        val over = b.level == BudgetLevel.Over
        val title = when {
            hide -> if (over) "Ada anggaran yang terlampaui" else "Ada anggaran yang hampir habis"
            over -> "Anggaran ${b.name} terlampaui ($pct%)"
            else -> "Anggaran ${b.name} sudah $pct%"
        }
        val text = if (hide) null else "Terpakai ${Rupiah.format(b.spent)} dari ${Rupiah.format(b.amount)}" +
            if (!over) " · sisa ${Rupiah.format(b.amount - b.spent)}." else "."
        val n = NotificationCompat.Builder(context, CHANNEL_BUDGETS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openModule("Finance"))
            .build()
        notify(("budget-" + b.id).hashCode(), n)
    }

    fun journal(streak: Int) {
        if (!canPost()) return
        ensureChannels()
        val n = NotificationCompat.Builder(context, CHANNEL_JOURNAL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("Streak jurnal $streak hari")
            .setContentText("Belum menulis hari ini — satu kalimat pun cukup supaya tidak putus.")
            .setAutoCancel(true)
            .setContentIntent(openModule("Journal"))
            .build()
        notify(ID_JOURNAL, n)
    }

    fun cancelBill(recurringId: String) = NotificationManagerCompat.from(context).cancel(billNotificationId(recurringId))

    private fun notify(id: Int, n: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    /** Buka modul lewat shortcut launcher yang sama dengan ModuleShortcuts aplikasi. */
    private fun openModule(module: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.setAction(ACTION_OPEN_MODULE)
            ?.setData(Uri.parse("taskify://module/$module"))
            ?.putExtra(EXTRA_MODULE, module)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: Intent()
        return PendingIntent.getActivity(context, module.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun billAction(recurringId: String, action: String): PendingIntent {
        val intent = Intent(context, BillActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("taskify://bill-action/$action/$recurringId"))
            .putExtra(BillActionReceiver.EXTRA_RECURRING_ID, recurringId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun billNotificationId(id: String) = ("bill-$id").hashCode()

    companion object {
        const val CHANNEL_BILLS = "bills"
        const val CHANNEL_BUDGETS = "budgets"
        const val CHANNEL_JOURNAL = "journal"
        const val ID_AUTO_POSTED = 41_001
        const val ID_JOURNAL = 41_002
        /** Sama dengan ModuleShortcuts di aplikasi. */
        const val ACTION_OPEN_MODULE = "com.rosyidputrar.taskify.action.OPEN_MODULE"
        const val EXTRA_MODULE = "module"
    }
}

/** Izin runtime + saklar notifikasi aplikasi (kanal masing-masing dicek sistem saat menampilkan). */
internal object NotificationGate {
    fun allowed(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
