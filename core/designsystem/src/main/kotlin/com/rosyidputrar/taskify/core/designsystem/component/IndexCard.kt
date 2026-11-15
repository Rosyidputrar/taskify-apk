package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/**
 * Kartu dengan "penanda indeks" di tepi kiri — seperti tab berwarna pada buku catatan.
 * Ini bahasa visual utama aplikasi: setiap kartu menunjukkan modul asalnya lewat tab, bukan gradasi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IndexCard(
    module: TaskifyModule,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    tinted: Boolean = false,
    tabWidth: Dp = 6.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (tinted) {
        TaskifyTheme.modules.tint(module, colors.surfaceContainerLow)
    } else {
        colors.surfaceContainerLow
    }
    val shape = MaterialTheme.shapes.large
    val interactive = if (onClick != null) {
        Modifier
            .clip(shape)
            .combinedClickable(onClickLabel = onClickLabel, onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Surface(modifier = modifier.then(interactive), shape = shape, color = container) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(vertical = 14.dp)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .background(
                        TaskifyTheme.modules.accent(module),
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    ),
            )
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
