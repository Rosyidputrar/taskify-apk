package com.rosyidputrar.taskify.feature.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Blok detail mentah error bersama (dipakai semua layar musik).
 *
 * Polanya sama dengan blok error login ([MusicLoginStateContent]):
 * judul "Detail mentah:", blok monospace maks 200.dp yang bisa di-scroll.
 * Bedanya: teks bisa diseleksi ([SelectionContainer]) dan — hanya di build
 * debug — ada tombol "Salin detail" yang menyalin SELURUH teks mentah ke
 * clipboard (lihat [copyRawDetailToClipboard]).
 *
 * [rawDetail] diasumsikan sudah dibersihkan dari nilai sensitif oleh
 * penyusunnya (mis. `PlaybackException.toRawDetail`): komponen ini
 * menampilkan apa adanya, tanpa memproses ulang isinya.
 *
 * @param showCopyButton tampilkan tombol salin; default hanya di build
 *   debug (`BuildConfig.DEBUG`). Parameter ini ada supaya Agen B / tes
 *   bisa memaksa tampil atau sembunyi tanpa mengubah build.
 */
@Composable
fun RawErrorDetail(
    rawDetail: String,
    modifier: Modifier = Modifier,
    showCopyButton: Boolean = BuildConfig.DEBUG,
) {
    val context = LocalContext.current
    Column(modifier) {
        Text(
            "Detail mentah:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    rawDetail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (showCopyButton) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (copyRawDetailToClipboard(context, rawDetail)) {
                        Toast.makeText(context, "Detail error disalin", Toast.LENGTH_SHORT).show()
                    }
                },
            ) { Text("Salin detail") }
        }
    }
}

/**
 * Salin SELURUH teks mentah ke clipboard sistem. `true` bila clipboard
 * berhasil ditulis. Dipisah dari composable supaya bisa diuji unit
 * (Robolectric) tanpa menjalankan Compose.
 */
fun copyRawDetailToClipboard(context: Context, rawDetail: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Detail error", rawDetail))
    return true
}
