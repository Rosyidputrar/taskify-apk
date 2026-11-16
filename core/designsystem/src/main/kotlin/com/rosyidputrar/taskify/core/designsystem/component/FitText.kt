package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Satu baris yang MENGECIL bila tidak muat (bukan terpotong "…" atau patah di tengah kata) —
 * untuk label tab, angka uang, dan ubin statistik saat pengguna memakai huruf besar.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 9.sp,
) {
    val c = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val max = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 14.sp
    BasicText(
        text, modifier, style = style.copy(color = c), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = max, stepSize = 0.5.sp),
    )
}
