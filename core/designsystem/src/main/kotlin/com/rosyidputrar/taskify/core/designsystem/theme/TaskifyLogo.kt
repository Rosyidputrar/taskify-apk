package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Logo aplikasi Taskify (kartu indeks + tab merah + centang) tanpa latar — path sama dengan
 * `ic_launcher_foreground` / `ic_launcher_monochrome`, dipotong ke area logo saja.
 */
object TaskifyLogo {
    // Logo menempati x 30–78, y 24–82 di viewport launcher 108; dibingkai 64×64 di tengahnya.
    private const val SIZE = 64f
    private const val DX = -22f
    private const val DY = -21f

    private const val TAB = "M60,24h12a4,4 0,0 1,4 4v10h-20v-10a4,4 0,0 1,4 -4z"
    private const val CARD = "M36,32h36a6,6 0,0 1,6 6v38a6,6 0,0 1,-6 6h-36a6,6 0,0 1,-6 -6v-38a6,6 0,0 1,6 -6z"
    private const val CHECK = "M41,58l9,9l17,-19"
    private const val CARD_WITH_CUTOUT = "${CARD}M43,56L50,63L65,46L69,50L50,71L39,60Z"

    /** Satu warna, centang berlubang (transparan) — ikut `tint` seperti ikon lain. */
    val Mono: ImageVector by lazy {
        ImageVector.Builder("TaskifyLogoMono", 24.dp, 24.dp, SIZE, SIZE).apply {
            addGroup(translationX = DX, translationY = DY)
            addPath(addPathNodes(TAB), fill = SolidColor(Color.Black))
            addPath(addPathNodes(CARD_WITH_CUTOUT), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
            clearGroup()
        }.build()
    }

    /** Warna asli logo (pakai dengan `tint = Color.Unspecified`). */
    val Full: ImageVector by lazy {
        ImageVector.Builder("TaskifyLogo", 24.dp, 24.dp, SIZE, SIZE).apply {
            addGroup(translationX = DX, translationY = DY)
            addPath(addPathNodes(TAB), fill = SolidColor(Color(0xFFA8341A)))
            addPath(addPathNodes(CARD), fill = SolidColor(Color(0xFF1D5867)))
            addPath(
                addPathNodes(CHECK), stroke = SolidColor(Color(0xFFFAF7F1)), strokeLineWidth = 6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
            clearGroup()
        }.build()
    }

    /**
     * Ikon modul Asisten AI: logo Taskify berbentuk GELEMBUNG CHAT — kartu berekor di kiri bawah,
     * tab di kanan atas, centang berlubang di dalam. Versi kotak biasa ([Mono]) di ukuran kecil
     * terbaca seperti checkbox tercentang ("☑ gpt-4.1-mini"); ekor gelembung membuatnya jelas
     * "asisten/chat" tanpa meninggalkan bentuk logo. Grid 24 seperti ikon Material lain.
     */
    val Assistant: ImageVector by lazy {
        ImageVector.Builder("TaskifyAssistant", 24.dp, 24.dp, 24f, 24f).apply {
            // Tab kartu (kanan atas), menyatu dengan badan gelembung.
            addPath(addPathNodes("M14.5,3h3a1.5,1.5 0,0 1,1.5 1.5V7H13V4.5A1.5,1.5 0,0 1,14.5,3z"), fill = SolidColor(Color.Black))
            // Badan gelembung + ekor; centang dilubangi (evenOdd).
            addPath(
                addPathNodes(
                    "M6,6h12a3,3 0,0 1,3,3v6a3,3 0,0 1,-3,3H10.5L6,21.5V18A3,3 0,0 1,3,15V9A3,3 0,0 1,6,6z" +
                        "M7.6,12.2L9,10.8L11,12.8L15,8.8L16.4,10.2L11,15.6z",
                ),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build()
    }
}
