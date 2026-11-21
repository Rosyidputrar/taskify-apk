package com.rosyidputrar.taskify.focus

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.R
import com.rosyidputrar.taskify.core.domain.focus.FocusPhase
import com.rosyidputrar.taskify.core.domain.focus.FocusRun
import com.rosyidputrar.taskify.core.domain.focus.MINUTE
import com.rosyidputrar.taskify.core.domain.focus.PomodoroConfig
import com.rosyidputrar.taskify.core.domain.focus.formatClock
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Satu sesi fokus yang selesai/dihentikan, siap dicatat ke riwayat (Review). */
data class FocusRecord(val taskId: String?, val taskTitle: String?, val day: String, val startedAt: String, val endedAt: String, val minutes: Long)

/** Jendela sesi fokus terakhir yang baru selesai (dipakai untuk Ringkasan Pasca-Fokus). */
data class EndedFocusWindow(val startedAtMs: Long, val endedAtMs: Long, val minutes: Long, val taskTitle: String?)

/**
 * Mode Fokus (Pomodoro) untuk seluruh aplikasi.
 *
 * Tanpa foreground service: notifikasi berjalan memakai hitung mundur bawaan sistem (chronometer)
 * dan akhir fase dibangunkan AlarmManager — hemat baterai dan tetap benar walau proses dimatikan,
 * karena status disimpan sebagai jam dinding ([FocusRun]). Saat aplikasi dibuka lagi, [catchUp]
 * menyusulkan fase yang terlewat.
 */
class FocusController(
    private val context: Context,
    private val record: suspend (FocusRecord) -> Unit,
    /** Pengaturan "Sembunyikan isi notifikasi": judul tugas tidak ditampilkan. */
    private val hideContent: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
    /** Otomatisasi Deep Work: nyalakan stasiun radio fokus saat fase Fokus mulai/lanjut. */
    private val onAutoMusicStart: (stationId: String) -> Unit = {},
    /** Otomatisasi Deep Work: jeda musik fokus saat istirahat/jeda/berhenti (hanya bila dinyalakan otomatis). */
    private val onAutoMusicPause: () -> Unit = {},
) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _run = MutableStateFlow(FocusRun.decode(prefs.getString(KEY_RUN, null)))
    val run: StateFlow<FocusRun?> = _run.asStateFlow()

    private val _config = MutableStateFlow(PomodoroConfig.decode(prefs.getString(KEY_CONFIG, null)))
    val config: StateFlow<PomodoroConfig> = _config.asStateFlow()

    private val _dnd = MutableStateFlow(prefs.getBoolean(KEY_DND, false))
    /** Pengguna ingin "Jangan Ganggu" menyala selama fase fokus. */
    val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

    private val _autoMusicStationId = MutableStateFlow(prefs.getString(KEY_AUTO_MUSIC, null))
    /** Stasiun radio fokus yang otomatis diputar selama fase Fokus (null = mati). */
    val autoMusicStationId: StateFlow<String?> = _autoMusicStationId.asStateFlow()

    private val _lastEndedFocus = MutableStateFlow<EndedFocusWindow?>(null)
    /** Sesi fokus terakhir yang baru tuntas, untuk menampilkan Ringkasan Notifikasi Pasca-Fokus. */
    val lastEndedFocus: StateFlow<EndedFocusWindow?> = _lastEndedFocus.asStateFlow()

    private var autoMusicActive = false

    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun setConfig(config: PomodoroConfig) {
        _config.value = config
        prefs.edit { putString(KEY_CONFIG, config.encode()) }
        // Durasi baru berlaku untuk fase BERIKUTNYA dari sesi yang sedang berjalan: config
        // tersimpan di dalam run (dipakai `next()`), jadi run lama tidak boleh menyimpan config lama.
        // Durasi fase yang sedang berjalan tidak diubah (akhir fase tetap seperti yang dijadwalkan).
        _run.value?.let { r ->
            val updated = r.copy(config = config)
            _run.value = updated
            prefs.edit { putString(KEY_RUN, updated.encode()) }
        }
    }

    fun setDnd(enabled: Boolean) {
        _dnd.value = enabled
        prefs.edit { putBoolean(KEY_DND, enabled) }
        _run.value?.let { applyDnd(it) }
    }

    fun setAutoMusicStation(stationId: String?) {
        _autoMusicStationId.value = stationId
        prefs.edit { if (stationId == null) remove(KEY_AUTO_MUSIC) else putString(KEY_AUTO_MUSIC, stationId) }
        applyAutoMusic(_run.value)
    }

    fun dismissDigest() {
        _lastEndedFocus.value = null
    }

    /** Izin akses "Jangan Ganggu" diberikan pengguna di Setelan sistem. */
    fun dndAccessGranted(): Boolean = notifications?.isNotificationPolicyAccessGranted == true

    suspend fun start(taskId: String?, taskTitle: String?) = mutex.withLock {
        _lastEndedFocus.value = null
        _run.value?.let { finishFocus(it, now()) }
        set(FocusRun.start(taskId, taskTitle, now(), _config.value))
    }

    suspend fun pause() = mutex.withLock { _run.value?.let { set(it.pause(now())) } }

    suspend fun resume() = mutex.withLock { _run.value?.let { set(it.resume(now())) } }

    /** Lewati ke fase berikutnya (fokus yang sudah dijalani tetap dicatat). */
    suspend fun skip() = mutex.withLock {
        val r = _run.value ?: return@withLock
        val t = now()
        finishFocus(r, t)
        set(r.next(t))
    }

    suspend fun stop() = mutex.withLock {
        val r = _run.value ?: return@withLock
        finishFocus(r, now())
        set(null)
    }

    /** Alarm akhir fase berbunyi / aplikasi dibuka: majukan fase yang sudah lewat. */
    suspend fun catchUp(alert: Boolean = true) = mutex.withLock { catchUpLocked(alert) }

    /**
     * Pemulihan setelah reboot / ganti waktu / izin alarm tepat berubah / proses mati paksa
     * (dipanggil FocusReceiver & TaskifyApplication.onCreate, tanpa perlu membuka aplikasi):
     * - ada sesi tersimpan → [catchUpLocked] memasang ulang alarm dari state itu + menata DND.
     * - tidak ada sesi tapi flag [KEY_DND_APPLIED] masih set → DND macet menyala dipulihkan.
     */
    suspend fun recoverAfterRestart() = mutex.withLock {
        val r = _run.value
        if (r == null) restoreDnd() else catchUpLocked(alert = false)
    }

    private suspend fun catchUpLocked(alert: Boolean) {
        var r = _run.value ?: return
        val t = now()
        if (!r.isFinished(t)) return refresh(r)
        // Fase bisa terlewat lebih dari satu (HP mati saat istirahat). Fokus yang menunggu "Mulai"
        // selalu dijeda, jadi rantai berhenti di sana.
        var steps = 0
        while (r.isFinished(t) && steps++ < 3) {
            val end = r.phaseStartedAt + r.durationMs
            finishFocus(r, end)
            val ended = r.phase
            r = r.next(end)
            if (alert) alertPhaseEnd(ended, r)
        }
        set(r)
    }

    /** Keluar akun: hentikan tanpa mencatat & bersihkan semuanya. */
    suspend fun clear() = mutex.withLock {
        _run.value = null
        // Pulihkan DND SEBELUM menghapus prefs: restoreDnd() membaca KEY_DND_APPLIED dari prefs.
        restoreDnd()
        if (autoMusicActive) {
            autoMusicActive = false
            runCatching { onAutoMusicPause() }
        }
        prefs.edit { clear() }
        _config.value = PomodoroConfig()
        _dnd.value = false
        _autoMusicStationId.value = null
        _lastEndedFocus.value = null
        cancelAlarm()
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        refreshTile()
    }

    private suspend fun finishFocus(r: FocusRun, at: Long) {
        val focused = r.focusedMs(at)
        if (r.phase != FocusPhase.Focus || focused < FocusRun.MIN_RECORD_MS) return
        val started = at - focused
        val minutes = (focused + MINUTE / 2) / MINUTE
        _lastEndedFocus.value = EndedFocusWindow(started, at, minutes, r.taskTitle)
        runCatching {
            record(FocusRecord(r.taskId, r.taskTitle, Dates.today(started), Dates.millisToIso(started), Dates.millisToIso(at), minutes))
        }
    }

    private fun set(r: FocusRun?) {
        // Urutan penting: atur alarm DULU, baru publikasikan state. `pause()` (dan aksi
        // notifikasi lain) berjalan async lewat FocusActionReceiver, dan pengamat `run` bisa
        // melihat sesi dijeda SEBELUM `cancelAlarm()` sempat jalan bila state
        // dipublikasikan dulu — alarm akhir fase yang masih terjadwal sempat terlihat
        // (flake di full suite, lolos bila dijalankan sendirian).
        applyAlarm(r)
        _run.value = r
        prefs.edit { if (r == null) remove(KEY_RUN) else putString(KEY_RUN, r.encode()) }
        if (r == null) {
            restoreDnd()
            NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        } else {
            applyDnd(r)
            postRunning(r)
        }
        applyAutoMusic(r)
        refreshTile()
    }

    private fun applyAutoMusic(r: FocusRun?) {
        val stationId = _autoMusicStationId.value
        val wantMusic = stationId != null && r != null && r.phase == FocusPhase.Focus && !r.isPaused
        if (wantMusic) {
            autoMusicActive = true
            runCatching { onAutoMusicStart(stationId!!) }
        } else if (autoMusicActive) {
            autoMusicActive = false
            runCatching { onAutoMusicPause() }
        }
    }

    private fun refresh(r: FocusRun) {
        applyAlarm(r)
        applyDnd(r)
        postRunning(r)
    }

    /** Batalkan alarm bila tidak ada sesi / sesi dijeda, pasang alarm akhir fase bila berjalan. */
    private fun applyAlarm(r: FocusRun?) {
        if (r == null || r.isPaused) cancelAlarm() else scheduleAlarm(r.endsAt(now()))
    }

    // ---- Jangan Ganggu -------------------------------------------------------------------------

    private fun applyDnd(r: FocusRun) {
        val manager = notifications ?: return
        val wantOn = _dnd.value && r.phase == FocusPhase.Focus && !r.isPaused
        if (wantOn) {
            if (!manager.isNotificationPolicyAccessGranted) return
            if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs.edit { putBoolean(KEY_DND_APPLIED, true) }
            }
        } else {
            restoreDnd()
        }
    }

    /** Kembalikan hanya bila KITA yang menyalakan dan pengguna belum mengubahnya sendiri. */
    private fun restoreDnd() {
        if (!prefs.getBoolean(KEY_DND_APPLIED, false)) return
        prefs.edit { remove(KEY_DND_APPLIED) }
        val manager = notifications ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    // ---- Alarm ---------------------------------------------------------------------------------

    private fun scheduleAlarm(at: Long) {
        val manager = alarms ?: return
        val pi = receiverIntent(FocusActionReceiver.ACTION_PHASE_END)
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun cancelAlarm() {
        alarms?.cancel(receiverIntent(FocusActionReceiver.ACTION_PHASE_END))
    }

    // ---- Notifikasi ----------------------------------------------------------------------------

    private fun ensureChannels() {
        notifications?.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, "Mode Fokus berjalan", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Hitung mundur sesi fokus yang sedang berjalan."
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_ALERT, "Mode Fokus: fase selesai", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Tanda waktu fokus atau istirahat sudah habis."
                },
            ),
        )
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // False positive lint: izin POST_NOTIFICATIONS SUDAH dicek di canPost()
    // (baris pertama metode ini) — lint tidak mengenali guard itu karena
    // notify() berada di dalam runCatching. Sama seperti DraftAlerts.
    @SuppressLint("MissingPermission")
    private fun postRunning(r: FocusRun) {
        if (!canPost()) return
        ensureChannels()
        val t = now()
        val title = if (hideContent()) "Mode Fokus" else r.taskTitle ?: "Fokus bebas"
        val phase = if (r.phase == FocusPhase.Focus) "Fokus · putaran ${r.round}" else r.phase.label
        val text = when {
            r.isPaused && r.phase == FocusPhase.Focus && r.elapsed(t) == 0L -> "Siap untuk putaran ${r.round} — ketuk Mulai"
            r.isPaused -> "Dijeda · sisa ${formatClock(r.remaining(t))}"
            else -> "$phase · selesai ${clockOf(r.endsAt(t))}"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_tile_focus)
            .setContentTitle(if (r.phase == FocusPhase.Focus) title else "${r.phase.label} sejenak")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openFocus())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_RUNNING)
                    .setSmallIcon(R.drawable.ic_tile_focus)
                    .setContentTitle("Mode Fokus")
                    .setContentText(phase)
                    .build(),
            )
            .setProgress(1000, (r.progress(t) * 1000).toInt(), false)
        if (!r.isPaused) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(r.endsAt(t)).setShowWhen(true)
            builder.addAction(0, "Jeda", receiverIntent(FocusActionReceiver.ACTION_PAUSE))
        } else {
            builder.setShowWhen(false)
            builder.addAction(0, if (r.elapsed(t) == 0L) "Mulai" else "Lanjut", receiverIntent(FocusActionReceiver.ACTION_RESUME))
        }
        builder.addAction(0, "Lewati", receiverIntent(FocusActionReceiver.ACTION_SKIP))
        builder.addAction(0, "Berhenti", receiverIntent(FocusActionReceiver.ACTION_STOP))
        runCatching { NotificationManagerCompat.from(context).notify(ID_RUNNING, builder.build()) }
    }

    // False positive lint: alasan sama seperti postRunning di atas
    // (canPost() di baris pertama, notify() di dalam runCatching).
    @SuppressLint("MissingPermission")
    private fun alertPhaseEnd(ended: FocusPhase, next: FocusRun) {
        if (!canPost()) return
        ensureChannels()
        val (title, text) = when (ended) {
            FocusPhase.Focus -> "Fokus selesai" to "Waktunya ${next.phase.label.lowercase()} ${next.durationMs / MINUTE} menit. Regangkan badan dan minum air."
            else -> "Istirahat selesai" to "Siap untuk putaran ${next.round}? Ketuk Mulai saat siap."
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_tile_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * MINUTE)
            .setContentIntent(openFocus())
            .build()
        // Satu id per fase yang berakhir: bila >1 fase terlewat (HP mati lama), tiap alert
        // terlihat sendiri, bukan hanya yang terakhir menimpa yang sebelumnya.
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALERT + ended.ordinal, n) }
    }

    private fun clockOf(millis: Long): String {
        val time = java.time.Instant.ofEpochMilli(millis).atOffset(Dates.WIB).toLocalTime()
        return "%02d.%02d".format(time.hour, time.minute)
    }

    private fun receiverIntent(action: String): PendingIntent {
        val intent = Intent(context, FocusActionReceiver::class.java).setAction(action).setData(Uri.parse("taskify://focus/$action"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openFocus(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun refreshTile() {
        runCatching { TileService.requestListeningState(context, ComponentName(context, FocusTileService::class.java)) }
    }

    companion object {
        private const val KEY_RUN = "run"
        private const val KEY_CONFIG = "config"
        private const val KEY_DND = "dnd"
        private const val KEY_DND_APPLIED = "dnd_applied"
        private const val KEY_AUTO_MUSIC = "auto_music_station"
        const val CHANNEL_RUNNING = "focus_running"
        const val CHANNEL_ALERT = "focus_alert"
        const val ID_RUNNING = 42_001
        const val ID_ALERT = 42_002
    }
}
