package com.rosyidputrar.taskify.core.domain.focus

/** Fase Pomodoro. */
enum class FocusPhase(val label: String) {
    Focus("Fokus"),
    ShortBreak("Istirahat"),
    LongBreak("Istirahat panjang"),
    ;

    val isBreak: Boolean get() = this != Focus
}

/** Durasi (menit) & jumlah putaran sebelum istirahat panjang. */
data class PomodoroConfig(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val roundsBeforeLong: Int = 4,
) {
    fun durationMs(phase: FocusPhase): Long = MINUTE * when (phase) {
        FocusPhase.Focus -> focusMinutes
        FocusPhase.ShortBreak -> shortBreakMinutes
        FocusPhase.LongBreak -> longBreakMinutes
    }

    fun encode(): String = "$focusMinutes,$shortBreakMinutes,$longBreakMinutes,$roundsBeforeLong"

    companion object {
        val FocusChoices = listOf(15, 25, 45, 60)
        fun decode(text: String?): PomodoroConfig {
            val n = text?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: return PomodoroConfig()
            if (n.size != 4 || n.any { it <= 0 } || n[0] > 180 || n[1] > 60 || n[2] > 90 || n[3] > 12) return PomodoroConfig()
            return PomodoroConfig(n[0], n[1], n[2], n[3])
        }
    }
}

const val MINUTE = 60_000L

/**
 * Satu sesi Mode Fokus yang sedang berjalan. Waktu disimpan sebagai jam dinding (epoch ms), bukan
 * hitungan detik, sehingga tetap benar walau proses aplikasi dimatikan lalu dibuka lagi.
 *
 * - [phaseStartedAt]: kapan fase ini mulai (sudah digeser maju sebesar total jeda).
 * - [pausedAt]: non-null = sedang dijeda. Fase fokus setelah istirahat menunggu di sini ("siap").
 * - [round]: putaran fokus ke-berapa (mulai 1).
 */
data class FocusRun(
    val taskId: String?,
    val taskTitle: String?,
    val phase: FocusPhase,
    val round: Int,
    val phaseStartedAt: Long,
    val durationMs: Long,
    val pausedAt: Long? = null,
    val config: PomodoroConfig = PomodoroConfig(),
) {
    val isPaused: Boolean get() = pausedAt != null

    /** Detik-jam (epoch ms) fase ini selesai bila tidak dijeda lagi. */
    fun endsAt(now: Long): Long = phaseStartedAt + durationMs + ((pausedAt?.let { now - it }) ?: 0L)

    fun elapsed(now: Long): Long = ((pausedAt ?: now) - phaseStartedAt).coerceIn(0L, durationMs)

    fun remaining(now: Long): Long = durationMs - elapsed(now)

    fun progress(now: Long): Float = if (durationMs <= 0) 1f else elapsed(now).toFloat() / durationMs

    fun isFinished(now: Long): Boolean = !isPaused && now >= phaseStartedAt + durationMs

    fun pause(now: Long): FocusRun = if (isPaused) this else copy(pausedAt = now)

    /** Lanjut: waktu jeda "dibuang" dengan menggeser awal fase maju. */
    fun resume(now: Long): FocusRun {
        val p = pausedAt ?: return this
        return copy(phaseStartedAt = phaseStartedAt + (now - p), pausedAt = null)
    }

    /**
     * Fase berikutnya. Setelah fokus → istirahat (panjang tiap [PomodoroConfig.roundsBeforeLong]
     * putaran) dan langsung berjalan; setelah istirahat → fokus putaran baru yang MENUNGGU diketuk
     * Mulai, supaya pengguna tidak "ketinggalan" sesi saat sedang jauh dari HP.
     */
    fun next(now: Long): FocusRun = when (phase) {
        FocusPhase.Focus -> {
            val long = round % config.roundsBeforeLong == 0
            val p = if (long) FocusPhase.LongBreak else FocusPhase.ShortBreak
            copy(phase = p, phaseStartedAt = now, durationMs = config.durationMs(p), pausedAt = null)
        }
        else -> copy(phase = FocusPhase.Focus, round = round + 1, phaseStartedAt = now, durationMs = config.durationMs(FocusPhase.Focus), pausedAt = now)
    }

    /** Fokus yang benar-benar dijalani (ms) dalam fase ini — untuk dicatat ke Review. */
    fun focusedMs(now: Long): Long = if (phase == FocusPhase.Focus) elapsed(now) else 0L

    fun encode(): String = listOf(
        taskId.orEmpty(), (taskTitle ?: "").replace("\u001F", " "), phase.name, round, phaseStartedAt, durationMs,
        pausedAt?.toString().orEmpty(), config.encode(),
    ).joinToString("\u001F")

    companion object {
        fun start(taskId: String?, taskTitle: String?, now: Long, config: PomodoroConfig = PomodoroConfig()) =
            FocusRun(taskId, taskTitle, FocusPhase.Focus, 1, now, config.durationMs(FocusPhase.Focus), null, config)

        fun decode(text: String?): FocusRun? {
            val p = text?.split("\u001F") ?: return null
            if (p.size != 8) return null
            return runCatching {
                FocusRun(
                    taskId = p[0].ifEmpty { null },
                    taskTitle = p[1].ifEmpty { null },
                    phase = FocusPhase.valueOf(p[2]),
                    round = p[3].toInt(),
                    phaseStartedAt = p[4].toLong(),
                    durationMs = p[5].toLong(),
                    pausedAt = p[6].ifEmpty { null }?.toLong(),
                    config = PomodoroConfig.decode(p[7]),
                )
            }.getOrNull()
        }

        /** Sesi fokus pendek (< 1 menit) tidak dicatat: kemungkinan salah ketuk. */
        const val MIN_RECORD_MS = MINUTE
    }
}

/** "24:59" / "1:05:00". */
fun formatClock(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "1 j 25 mnt" / "40 mnt". */
fun formatMinutes(minutes: Long): String = when {
    minutes >= 60 && minutes % 60 == 0L -> "${minutes / 60} j"
    minutes >= 60 -> "${minutes / 60} j ${minutes % 60} mnt"
    else -> "$minutes mnt"
}
