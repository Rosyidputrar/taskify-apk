package com.rosyidputrar.taskify.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Suasana logo hidup. */
enum class LogoMood {
    /** Diam yang hidup: bernapas pelan, tab bergoyang, centang sesekali tergambar ulang. */
    Idle,
    /** Sedang bekerja (mis. AI menyiapkan jawaban): napas lebih cepat, centang tergambar berulang. */
    Thinking,
}

/**
 * Logo Taskify yang "hidup" — kartu indeks, tab merah, dan centang digerakkan terpisah.
 *
 * - Muncul: kartu membesar dengan pegas, tab jatuh ke tempatnya, centang tergambar seperti ditulis.
 * - [LogoMood.Idle]: semua gerak berasal dari satu jam sinus (tanpa sentakan), napas kartu ±2,5%,
 *   tab mengikuti sedikit terlambat, dan tiap 8 detik centang memudar lalu ditulis ulang.
 * - [LogoMood.Thinking]: siklus 1,6 detik — centang ditulis, ditahan, memudar, berulang.
 * - [tint] null = warna asli; diisi = satu warna dengan centang berlubang (transparan).
 * - Hormati "Hapus animasi" sistem: logo tampil diam.
 */
@Composable
fun LivingLogo(
    modifier: Modifier = Modifier,
    mood: LogoMood = LogoMood.Idle,
    tint: Color? = null,
    intro: Boolean = true,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val paths = remember { LogoPaths() }

    // ---- muncul (sekali)
    val card = remember { Animatable(if (intro && !still) 0f else 1f) }
    val tabDrop = remember { Animatable(if (intro && !still) 1f else 0f) }
    val write = remember { Animatable(if (intro && !still) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!intro || still) return@LaunchedEffect
        listOf(
            async { card.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) },
            async { delay(140); tabDrop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) },
            async { delay(300); write.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) },
        ).awaitAll()
    }

    // ---- hidup (berulang); satu jam linear 0..1, semua gerak diturunkan darinya → mulus & selalu tersambung
    val period = if (mood == LogoMood.Thinking) 1600 else 8000
    val clock = rememberInfiniteTransition(label = "logo")
    val t by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "jam")
    val time = if (still) 0f else t

    val desc = contentDescription
    Canvas(
        modifier
            .then(if (tint != null) Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) else Modifier)
            .then(if (desc != null) Modifier.semantics { this.contentDescription = desc } else Modifier),
    ) {
        val f = Frame.at(time, mood)
        val u = size.minDimension / LogoPaths.SIZE
        translate((size.width - u * LogoPaths.SIZE) / 2f, (size.height - u * LogoPaths.SIZE) / 2f) {
            scale(u, u, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                val cardScale = (0.55f + 0.45f * card.value) * f.breath
                // Tab: di belakang kartu, jatuh saat muncul lalu bergoyang dari engselnya.
                translate(0f, f.tabLift - 12f * tabDrop.value) {
                    rotate(f.tabTilt, pivot = LogoPaths.TAB_HINGE) {
                        scale(cardScale, cardScale, pivot = LogoPaths.CARD_CENTER) {
                            drawPath(paths.tab, tint ?: LogoPaths.RED, alpha = (1f - tabDrop.value).coerceIn(0f, 1f))
                        }
                    }
                }
                scale(cardScale, cardScale, pivot = LogoPaths.CARD_CENTER) {
                    drawPath(paths.card, tint ?: LogoPaths.TEAL, alpha = card.value.coerceIn(0f, 1f))
                    drawCheck(paths, progress = write.value * f.check, alpha = f.checkAlpha, cutout = tint != null)
                }
            }
        }
    }
}

/** Posisi semua bagian pada satu titik waktu [t] (0..1) — fungsi murni supaya mudah diuji & mulus. */
internal data class Frame(val breath: Float, val tabLift: Float, val tabTilt: Float, val check: Float, val checkAlpha: Float) {
    companion object {
        private const val TAU = (2 * PI).toFloat()

        fun at(t: Float, mood: LogoMood): Frame = when (mood) {
            LogoMood.Idle -> {
                // Dua napas per siklus (4 dtk); tab tertinggal sedikit (follow-through).
                val breath = 1f + 0.025f * sin(TAU * 2 * t)
                val lift = -1.4f * (0.5f - 0.5f * cos(TAU * 2 * t - 0.7f))
                val tilt = 3f * sin(TAU * 2 * t - 1.3f)
                // Centang: tetap → memudar (0,74–0,80) → ditulis ulang (0,82–0,96).
                val (check, alpha) = when {
                    t < 0.74f -> 1f to 1f
                    t < 0.80f -> 1f to 1f - ease((t - 0.74f) / 0.06f)
                    t < 0.82f -> 0f to 1f
                    t < 0.96f -> ease((t - 0.82f) / 0.14f) to 1f
                    else -> 1f to 1f
                }
                Frame(breath, lift, tilt, check, alpha)
            }
            LogoMood.Thinking -> {
                val breath = 1f + 0.04f * sin(TAU * t)
                val lift = -2.2f * (0.5f - 0.5f * cos(TAU * t - 0.6f))
                val tilt = 5f * sin(TAU * t - 1.1f)
                val (check, alpha) = when {
                    t < 0.5f -> ease(t / 0.5f) to 1f
                    t < 0.78f -> 1f to 1f
                    else -> 1f to 1f - ease((t - 0.78f) / 0.22f)
                }
                Frame(breath, lift, tilt, check, alpha)
            }
        }

        /** Ease in-out kubik — awal & akhir melambat, tanpa sentakan. */
        internal fun ease(x: Float): Float {
            val v = x.coerceIn(0f, 1f)
            return if (v < 0.5f) 4 * v * v * v else 1 - (-2 * v + 2).let { it * it * it } / 2
        }
    }
}

private fun DrawScope.drawCheck(paths: LogoPaths, progress: Float, alpha: Float, cutout: Boolean) {
    if (progress <= 0f || alpha <= 0f) return
    val segment = if (progress >= 1f) paths.check else Path().also { paths.checkMeasure.getSegment(0f, paths.checkLength * progress, it, true) }
    drawPath(
        segment,
        color = if (cutout) Color.Black else LogoPaths.PAPER,
        alpha = alpha,
        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (cutout) BlendMode.DstOut else BlendMode.SrcOver,
    )
}

/** Path logo (sama dengan ic_launcher_foreground), digeser ke bingkai 64×64. */
private class LogoPaths {
    val tab = parse("M38,3h12a4,4 0,0 1,4 4v10h-20v-10a4,4 0,0 1,4 -4z")
    val card = parse("M14,11h36a6,6 0,0 1,6 6v38a6,6 0,0 1,-6 6h-36a6,6 0,0 1,-6 -6v-38a6,6 0,0 1,6 -6z")
    val check = parse("M19,37l9,9l17,-19")
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val checkLength = checkMeasure.length

    private fun parse(d: String) = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.NonZero }

    companion object {
        const val SIZE = 64f
        val CARD_CENTER = androidx.compose.ui.geometry.Offset(32f, 36f)
        val TAB_HINGE = androidx.compose.ui.geometry.Offset(46f, 17f)
        val RED = Color(0xFFA8341A)
        val TEAL = Color(0xFF1D5867)
        val PAPER = Color(0xFFFAF7F1)
    }
}
