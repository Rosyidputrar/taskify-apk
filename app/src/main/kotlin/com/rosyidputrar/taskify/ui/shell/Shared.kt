package com.rosyidputrar.taskify.ui.shell

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

/** Scope SharedTransitionLayout yang membungkus NavHost (null di luar NavHost, mis. pratinjau/tes). */
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Scope animasi destinasi NavHost yang sedang dirender (disediakan tiap blok `composable`). */
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Batas elemen bersama dipindah dengan pegas — kartu "terbang" ke posisinya di layar tujuan. */
val SpringBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.86f, stiffness = 380f) }

/**
 * Elemen bersama antar layar (kartu tugas Beranda → baris tugas di layar Tugas). No-op bila tidak
 * berada di dalam SharedTransitionLayout/NavHost atau [key] null, jadi komponen tetap bisa dipakai
 * di mana saja (termasuk tes tangkapan layar).
 */
fun Modifier.sharedBounds(key: String?): Modifier = composed {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (key == null || shared == null || nav == null) return@composed this
    with(shared) {
        this@composed.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = nav,
            boundsTransform = SpringBounds,
        )
    }
}

/** Spesifikasi gerak transisi layar — satu sumber di [TaskifyMotion] (dipakai juga modul fitur). */
object Motion {
    val screenOffset: FiniteAnimationSpec<IntOffset> get() = TaskifyMotion.screenOffset
    val screenFloat: FiniteAnimationSpec<Float> get() = TaskifyMotion.screenFloat
}
