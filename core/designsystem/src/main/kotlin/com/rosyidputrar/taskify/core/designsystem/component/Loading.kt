@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/**
 * Indikator memuat Taskify: M3 Expressive [LoadingIndicator] (bentuk yang berubah-ubah sambil
 * berputar). Ukuran lazim: 18/24 dp di dalam tombol & baris, 40 dp di dialog & halaman.
 * Warna mengikuti aksen [module] bila diisi.
 */
@Composable
fun TaskifyLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 40.dp, module: TaskifyModule? = null, color: Color = Color.Unspecified) {
    val tint = when {
        color != Color.Unspecified -> color
        module != null -> TaskifyTheme.modules.accent(module)
        else -> MaterialTheme.colorScheme.primary
    }
    LoadingIndicator(modifier = modifier.size(size), color = tint)
}

/** Indikator + label di tengah area — untuk halaman/editor yang menunggu satu hal. */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier, label: String? = "Memuat…", module: TaskifyModule? = null) {
    Column(
        modifier
            .padding(vertical = 36.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label ?: "Memuat"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskifyLoadingIndicator(module = module)
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [LoadingBlock] memenuhi layar (editor yang belum punya isi sama sekali). */
@Composable
fun LoadingScreen(label: String? = "Memuat…", module: TaskifyModule? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBlock(label = label, module = module) }
}

// ------------------------------------------------------------------ skeleton

/**
 * Kilau pelan yang menyapu kerangka dari kiri ke kanan — tanda "sedang dimuat" tanpa menarik
 * perhatian. Satu transisi per elemen; murah karena hanya menggambar gradien di atas latar.
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-x",
    )
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(w * x - w * 0.4f, 0f),
                end = Offset(w * x, size.height),
            ),
        )
    }
}

/** Satu batang kerangka (pengganti baris teks). */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Dp = 12.dp) {
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/** Kerangka satu kartu indeks: pita aksen modul di kiri, judul, dan dua baris isi. */
@Composable
fun SkeletonCard(module: TaskifyModule?, modifier: Modifier = Modifier, lines: Int = 2, leadingDot: Boolean = true) {
    val accent = module?.let { TaskifyTheme.modules.accent(it) } ?: MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .shimmer()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height((20 + lines * 16).dp).clip(CircleShape).background(accent.copy(alpha = 0.35f)))
        Spacer(Modifier.width(12.dp))
        if (leadingDot) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.62f, height = 14.dp)
            repeat(lines) { i -> SkeletonLine(widthFraction = if (i == lines - 1) 0.4f else 0.85f, height = 10.dp) }
        }
    }
}

/**
 * Kerangka daftar: [rows] kartu dengan lebar baris bervariasi. Dibaca TalkBack sebagai "Memuat…".
 * Dipakai di tempat daftar akan muncul sampai data pertama dari database lokal tiba.
 */
@Composable
fun ListSkeleton(module: TaskifyModule?, modifier: Modifier = Modifier, rows: Int = 4, lines: Int = 1, label: String = "Memuat…") {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) { SkeletonCard(module, lines = lines) }
    }
}

/** Indikator kecil + label sebaris — untuk proses yang sedang berjalan di dalam kartu/form. */
@Composable
fun LoadingRow(label: String?, modifier: Modifier = Modifier, module: TaskifyModule? = null, size: Dp = 24.dp) {
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = label ?: "Memuat"
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskifyLoadingIndicator(size = size, module = module)
        if (label != null) {
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
