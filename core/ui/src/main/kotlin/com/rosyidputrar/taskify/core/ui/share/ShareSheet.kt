package com.rosyidputrar.taskify.core.ui.share

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.text.ShareFormat

data class ShareOption<T>(val value: T, val title: String, val hint: String, val icon: ImageVector)

data class ShareSection<T>(val label: String?, val options: List<ShareOption<T>>)

/** Pilihan bagikan dokumen (catatan, jurnal): kirim teks (null) atau berkas [ShareFormat]. */
fun docShareSections(): List<ShareSection<ShareFormat?>> = listOf(
    ShareSection(null, listOf(ShareOption(null, "Kirim sebagai teks", "Langsung ke chat atau surel, tanpa berkas", Icons.AutoMirrored.Outlined.Chat))),
    ShareSection(
        "Sebagai berkas",
        ShareFormat.entries.map { f ->
            val icon = when (f) {
                ShareFormat.Markdown -> Icons.Outlined.Code
                ShareFormat.Text -> Icons.Outlined.Description
                ShareFormat.Html -> Icons.Outlined.Language
                ShareFormat.Pdf -> Icons.Outlined.PictureAsPdf
                ShareFormat.Image -> Icons.Outlined.Image
            }
            ShareOption(f, "${f.label} (.${f.extension})", f.hint, icon)
        },
    ),
)

/** Lembar bagikan bersama: judul, lalu bagian-bagian pilihan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ShareSheet(title: String, module: TaskifyModule, sections: List<ShareSection<T>>, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShareOptions(title, module, sections, onPick, Modifier.navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
fun <T> ShareOptions(title: String, module: TaskifyModule, sections: List<ShareSection<T>>, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    val accent = TaskifyTheme.modules.accent(module)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        sections.forEach { section ->
            section.label?.let {
                Text(
                    it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            section.options.forEach { o -> ShareRow(o.icon, o.title, o.hint, accent) { onPick(o.value) } }
        }
    }
}

@Composable
private fun ShareRow(icon: ImageVector, title: String, hint: String, accent: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        leadingContent = {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

/** Android 13+ sudah menampilkan konfirmasi salin sendiri; versi lama diberi Toast. */
fun confirmCopied(context: Context, what: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "$what disalin", Toast.LENGTH_SHORT).show()
}
