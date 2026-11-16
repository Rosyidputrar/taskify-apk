package com.rosyidputrar.taskify.feature.media

import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.TikTok
import com.rosyidputrar.taskify.core.domain.media.TikTokAsset
import com.rosyidputrar.taskify.core.media.SavedMedia
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TikTokScreen(contentPadding: PaddingValues, initial: String? = null, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val vm: TikTokViewModel = koinViewModel(key = "tiktok-screen")
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(initial) { if (!initial.isNullOrBlank() && state.post == null) vm.extract(initial) }
    CompositionLocalProvider(LocalWorkerOrigin provides koinInject<TikTokConfig>().origin) {
        TikTokContent(state, vm, contentPadding, onBack, backButton)
    }
}

/** Origin untuk gambar dari Worker TikTok (diisi layar; kosong di pratinjau/tes). */
val LocalWorkerOrigin = staticCompositionLocalOf { "" }

@Composable
fun TikTokContent(state: TikTokUiState, actions: TikTokActions, contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(TaskifyModule.TikTok, "TikTok", subtitle = "Lihat & simpan video, foto, dan audio", navigation = onBack?.let { { backButton(it) } })
        }
        if (!state.configured) {
            item(key = "off") { EmptyState(TaskifyModule.TikTok, "Pengunduh TikTok belum diatur", "Isi TIKTOK_API_URL dan PUBLIC_WEB_ORIGIN saat build (GitHub Secrets).") }
            return@LazyColumn
        }
        item(key = "input") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state.input, actions::setInput, placeholder = { Text("Tempel link TikTok…") }, singleLine = true, shape = MaterialTheme.shapes.large,
                    isError = state.error != null, supportingText = state.error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { actions.extract() }),
                    trailingIcon = {
                        IconButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { actions.setInput(it) }
                        }) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Tempel dari papan klip") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { actions.extract() }, enabled = !state.loading && state.input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "Mengambil…" else "Ambil") }
                if (state.loading) LoadingRow("Mengambil video…", module = TaskifyModule.Music)
            }
        }
        if (state.post != null) item(key = "card") { TikTokCard(state, actions, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        else if (!state.loading && state.error == null) item(key = "hint") {
            EmptyState(TaskifyModule.TikTok, "Dari aplikasi TikTok", "Bagikan → Salin tautan, lalu tempel di sini. Atau langsung Bagikan → Taskify.")
        }
    }
}

/** Gambar dari Worker: header Origin wajib (Worker hanya melayani Origin Taskify). */
@Composable
private fun WorkerImage(url: String, modifier: Modifier, contentDescription: String?) {
    val context = LocalContext.current
    val origin = LocalWorkerOrigin.current
    val request = ImageRequest.Builder(context).data(url)
        .apply { if (origin.isNotBlank()) httpHeaders(NetworkHeaders.Builder().set("Origin", origin).build()) }.build()
    AsyncImage(request, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TikTokCard(state: TikTokUiState, actions: TikTokActions, modifier: Modifier = Modifier) {
    val post = state.post ?: return
    val context = LocalContext.current
    val accent = TaskifyTheme.modules.accent(TaskifyModule.TikTok)
    // Bukan IndexCard: HorizontalPager (SubcomposeLayout) tidak mendukung pengukuran intrinsik kartu itu.
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        if (post.isPhoto && post.photoUrls.isNotEmpty()) {
            val pager = rememberPagerState { post.photoUrls.size }
            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(16.dp))) { i ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    WorkerImage(post.photoUrls[i], Modifier.fillMaxSize(), "Foto ${i + 1} dari ${post.photoUrls.size}")
                }
            }
            Text("${pager.currentPage + 1} / ${post.photoUrls.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        } else post.cover?.let {
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                WorkerImage(it, Modifier.fillMaxSize(), "Sampul video")
                post.durationSeconds?.let { d ->
                    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                        Text(MusicQueue.formatClock(d.toLong()), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(post.authorName.ifBlank { "@${post.authorId}" }, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (post.verified) Icon(Icons.Outlined.Verified, contentDescription = "Terverifikasi", tint = accent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(post.canonicalUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Buka di TikTok")
            }
        }
        Text("@${post.authorId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (post.description.isNotBlank()) Text(post.description, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            "${TikTok.compactCount(post.plays)} ditonton · ${TikTok.compactCount(post.likes)} suka · ${TikTok.compactCount(post.comments)} komentar",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
        post.musicTitle?.let { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Outlined.MusicNote, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" $m" + (post.musicAuthor?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(10.dp))
        val assets = buildList {
            if (post.videoUrl != null) add(TikTokAsset.Video to "Video")
            post.photoUrls.indices.forEach { add(TikTokAsset.Photo(it) to "Foto ${it + 1}") }
            if (post.audioUrl != null) add(TikTokAsset.Audio to "Audio")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            assets.forEach { (asset, label) -> AssetButton(label, state.downloading[asset.key()], state.downloading.containsKey(asset.key()), state.saved[asset.key()]) { actions.download(asset) } }
        }
        if (post.photoUrls.size > 1) TextButton(onClick = { post.photoUrls.indices.forEach { actions.download(TikTokAsset.Photo(it)) } }) {
            Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(6.dp)); Text("Unduh semua foto (${post.photoUrls.size})")
        }
    }
    }
}

@Composable
private fun AssetButton(label: String, progress: Float?, running: Boolean, saved: SavedMedia?, onClick: () -> Unit) {
    val context = LocalContext.current
    if (saved != null) {
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType(context.contentResolver.getType(saved.uri) ?: "*/*").putExtra(Intent.EXTRA_STREAM, saved.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, "Bagikan $label").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("$label tersimpan") }
        return
    }
    Button(onClick = onClick, enabled = !running) {
        if (running) Text("$label ${progress?.let { "${(it * 100).toInt()}%" } ?: "…"}")
        else { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label) }
    }
}
