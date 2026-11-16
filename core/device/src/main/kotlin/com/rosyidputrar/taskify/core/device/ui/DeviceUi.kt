package com.rosyidputrar.taskify.core.device.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.device.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AppInfo dari Koin bila tersedia (null di pratinjau/tes → ikon huruf). */
@Composable
fun rememberAppInfo(): AppInfo? = remember {
    runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<AppInfo>() }.getOrNull()
}

/** Palet kategorikal untuk aplikasi di grafik (urutan peringkat → warna tetap dalam satu layar). */
val APP_COLOR_NAMES = listOf("indigo", "rose", "amber", "teal", "violet", "emerald", "orange", "sky")

@Composable
fun appColor(index: Int): Color = if (index in APP_COLOR_NAMES.indices) namedColor(APP_COLOR_NAMES[index]) else MaterialTheme.colorScheme.outline

/** Warna stabil untuk huruf awal aplikasi (bukan peringkat). */
@Composable
fun stableAppColor(pkg: String): Color = namedColor(APP_COLOR_NAMES[(pkg.hashCode() and Int.MAX_VALUE) % APP_COLOR_NAMES.size])

/** Ikon aplikasi lain; huruf awal berwarna bila ikon tidak tersedia. Dekoratif (label ada di teks). */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val info = rememberAppInfo()
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, pkg, info) {
        value = info?.let { withContext(Dispatchers.IO) { it.icon(pkg, px)?.asImageBitmap() } }
    }
    val shape = RoundedCornerShape(size / 4)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        val color = stableAppColor(pkg)
        Box(modifier.size(size).clip(shape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(label.trim().take(1).uppercase().ifEmpty { "•" }, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}
