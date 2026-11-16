package com.rosyidputrar.taskify.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.FileKind
import com.rosyidputrar.taskify.core.domain.files.TempFiles

private fun FileKind.look(): Pair<ImageVector, String> = when (this) {
    FileKind.Image -> Icons.Outlined.Image to "rose"
    FileKind.Video -> Icons.Outlined.Movie to "violet"
    FileKind.Audio -> Icons.Outlined.MusicNote to "fuchsia"
    FileKind.Archive -> Icons.Outlined.FolderZip to "amber"
    FileKind.Sheet -> Icons.Outlined.TableChart to "emerald"
    FileKind.Code -> Icons.Outlined.Code to "sky"
    FileKind.Document -> Icons.Outlined.Description to "teal"
    FileKind.Other -> Icons.AutoMirrored.Outlined.InsertDriveFile to "slate"
}

/** Ikon jenis berkas di atas petak berwarna (MIME dulu, lalu ekstensi). */
@Composable
fun FileGlyph(mime: String, name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val (icon, color) = TempFiles.kind(mime, name).look()
    val c = namedColor(color)
    Box(modifier.size(size).background(c.copy(alpha = 0.16f), RoundedCornerShape(size / 3.2f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(size / 2))
    }
}
