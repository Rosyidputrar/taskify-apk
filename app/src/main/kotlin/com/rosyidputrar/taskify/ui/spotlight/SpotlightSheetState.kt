package com.rosyidputrar.taskify.ui.spotlight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Posisi panel Spotlight yang mengikuti jari: [progress] 0 = tertutup, 1 = terbuka penuh.
 *
 * Saat dok (atau panel) diseret, panel bergerak 1:1 dengan jari. Dilepas → terbuka bila sudah
 * lewat [OPEN_THRESHOLD] atau dikibas ke atas, tertutup bila dikibas ke bawah atau belum cukup
 * tinggi; animasi pegas meneruskan kecepatan jari sehingga tidak terasa "patah".
 */
@Stable
class SpotlightSheetState internal constructor(private val scope: CoroutineScope, initiallyOpen: Boolean) {
    private val anim = Animatable(if (initiallyOpen) 1f else 0f)

    /** 0..1, dibaca di fase gambar (graphicsLayer) agar tidak memicu recomposition tiap frame. */
    val progress: Float get() = anim.value

    /** Jarak seret (px) dari tertutup ke terbuka penuh; diisi dari ukuran layar. */
    var travelPx by mutableFloatStateOf(DEFAULT_TRAVEL_PX)

    /** Tujuan terakhir: true = terbuka. Selama diseret mengikuti posisi saat ini. */
    var isOpen by mutableStateOf(initiallyOpen)
        private set

    /** Sedang diseret jari (keyboard & fokus ditahan sampai panel diam). */
    var dragging by mutableStateOf(false)
        private set

    /** Panel tampil (sebagian atau penuh). derivedStateOf: tidak memicu recomposition tiap frame. */
    val visible: Boolean by derivedStateOf { isOpen || dragging || anim.value > 0f }

    /** Terbuka penuh dan diam — saat itulah kolom cari boleh mengambil fokus. */
    val settledOpen: Boolean by derivedStateOf { isOpen && !dragging && anim.value >= 1f && !anim.isRunning }

    /** [deltaY] dalam px; negatif = jari naik (membuka). Mengembalikan true bila melewati ambang buka. */
    fun dragBy(deltaY: Float): Boolean {
        dragging = true
        val before = anim.value
        val after = (before - deltaY / travelPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(after) }
        return (before < OPEN_THRESHOLD) != (after < OPEN_THRESHOLD)
    }

    /** Jari dilepas dengan kecepatan [velocityY] px/dtk (negatif = ke atas). */
    fun release(velocityY: Float) {
        dragging = false
        val open = when {
            velocityY < -FLING_VELOCITY -> true
            velocityY > FLING_VELOCITY -> false
            else -> anim.value >= OPEN_THRESHOLD
        }
        settle(open, -velocityY / travelPx.coerceAtLeast(1f))
    }

    fun open() = settle(true, 0f)

    fun close() = settle(false, 0f)

    private fun settle(open: Boolean, velocity: Float) {
        isOpen = open
        scope.launch {
            anim.animateTo(
                if (open) 1f else 0f,
                spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = velocity,
            )
        }
    }

    companion object {
        const val OPEN_THRESHOLD = 0.4f
        /** px/dtk — kibasan lebih cepat dari ini langsung membuka/menutup. */
        const val FLING_VELOCITY = 1_200f
        private const val DEFAULT_TRAVEL_PX = 900f
    }
}

@Composable
fun rememberSpotlightSheetState(): SpotlightSheetState {
    val scope = rememberCoroutineScope()
    var savedOpen by rememberSaveable { mutableStateOf(false) }
    val state = remember { SpotlightSheetState(scope, savedOpen) }
    LaunchedEffect(state.isOpen) { savedOpen = state.isOpen }
    return state
}
