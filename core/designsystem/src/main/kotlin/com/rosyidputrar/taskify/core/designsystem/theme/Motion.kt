package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

/**
 * Satu sumber spesifikasi gerak untuk seluruh aplikasi (shell, NavHost, dan layar di modul fitur).
 *
 * - **Geser** (push/pop, halaman dalam, ganti bulan): pegas hampir tanpa pantulan, pola Animeru.
 * - **Pudar-silang tanpa tumpuk** ("fade through"): layar lama hilang dulu (90 ms), baru layar baru
 *   muncul (210 ms). Dipakai untuk tab dok, editor layar penuh, gerbang kunci, dan label status —
 *   supaya dua isi tidak pernah tampil bertumpuk di tengah animasi.
 * - **Krom** (dok, bilah musik mini): naik/turun dengan pegas yang sama.
 */
object TaskifyMotion {
    /** Geser layar: pegas hampir tanpa pantulan. */
    val screenOffset: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.95f, stiffness = 420f, visibilityThreshold = IntOffset(1, 1))

    /** Pudar & skala yang menyertai geser. */
    val screenFloat: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 420f)

    /** Ukuran (Dp) yang ikut bergerak bersama layar/krom, mis. ruang bilah mini. */
    val screenDpSpec: FiniteAnimationSpec<Dp> = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = Dp.VisibilityThreshold)

    const val FADE_OUT_MS = 90
    const val FADE_IN_MS = 210

    private val fadeOutSpec: FiniteAnimationSpec<Float> = tween(FADE_OUT_MS, easing = FastOutLinearInEasing)
    private val fadeInSpec: FiniteAnimationSpec<Float> = tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing)

    /** Masuk "fade through": menunggu yang lama hilang, lalu muncul (+ skala halus bila [initialScale] < 1). */
    fun fadeThroughIn(initialScale: Float = 1f): EnterTransition =
        if (initialScale >= 1f) fadeIn(fadeInSpec) else fadeIn(fadeInSpec) + scaleIn(fadeInSpec, initialScale)

    /** Keluar "fade through": hilang cepat (+ mengecil bila [targetScale] < 1). */
    fun fadeThroughOut(targetScale: Float = 1f): ExitTransition =
        if (targetScale >= 1f) fadeOut(fadeOutSpec) else fadeOut(fadeOutSpec) + scaleOut(fadeOutSpec, targetScale)

    /** Krom bawah (dok, bilah mini) muncul: naik dari bawah. */
    val chromeEnter: EnterTransition = slideInVertically(screenOffset) { it } + fadeIn(screenFloat)

    /** Krom bawah hilang: turun ke bawah. */
    val chromeExit: ExitTransition = slideOutVertically(screenOffset) { it } + fadeOut(screenFloat)
}
