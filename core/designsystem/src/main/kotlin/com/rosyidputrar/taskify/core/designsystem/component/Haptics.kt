package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Saklar "Getaran" di Pengaturan (per perangkat). Bawaan menyala. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Getaran Taskify berdasarkan MAKNA, bukan jenis sentuhan — supaya terasa konsisten di semua modul:
 * - [success]: sesuatu berhasil (tugas selesai, tersalin, brankas terbuka);
 * - [error]: ditolak (PIN / master password salah);
 * - [tick]: memilih di antara pilihan (tab, segmen, tanggal, mood);
 * - [toggle]: saklar / centang nyala-mati;
 * - [threshold]: geseran melewati titik aksi (geser selesai/hapus, tarik dok);
 * - [tap]: tombol utama (Tangkap, kunci sekarang);
 * - [key]: tombol papan angka PIN;
 * - [longPress]: tahan lama membuka pilihan.
 * Menghormati saklar sistem "Getaran sentuh" dan saklar "Getaran" Taskify ([LocalHapticsEnabled]).
 */
@Stable
class TaskifyHaptics internal constructor(private val feedback: HapticFeedback, private val enabled: () -> Boolean) {
    private fun play(type: HapticFeedbackType) {
        if (enabled()) runCatching { feedback.performHapticFeedback(type) }
    }

    fun success() = play(HapticFeedbackType.Confirm)
    fun error() = play(HapticFeedbackType.Reject)
    fun tick() = play(HapticFeedbackType.SegmentTick)
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun threshold() = play(HapticFeedbackType.GestureThresholdActivate)
    fun tap() = play(HapticFeedbackType.ContextClick)
    fun key() = play(HapticFeedbackType.VirtualKey)
    fun longPress() = play(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): TaskifyHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled by rememberUpdatedState(LocalHapticsEnabled.current)
    return remember(feedback) { TaskifyHaptics(feedback) { enabled } }
}
