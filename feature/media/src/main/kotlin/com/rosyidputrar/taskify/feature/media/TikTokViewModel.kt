package com.rosyidputrar.taskify.feature.media

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.media.TikTok
import com.rosyidputrar.taskify.core.domain.media.TikTokAsset
import com.rosyidputrar.taskify.core.domain.media.TikTokPost
import com.rosyidputrar.taskify.core.media.MediaSaver
import com.rosyidputrar.taskify.core.media.SavedMedia
import com.rosyidputrar.taskify.core.media.TikTokClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Asal yang dikirim sebagai header Origin ke Worker (gambar pratinjau juga). */
data class TikTokConfig(val origin: String)

data class TikTokUiState(
    val configured: Boolean = true,
    val input: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val post: TikTokPost? = null,
    val extractedAt: Long = 0,
    /** Kunci aset → progres 0..1 (null = tidak diketahui). */
    val downloading: Map<String, Float?> = emptyMap(),
    val saved: Map<String, SavedMedia> = emptyMap(),
)

interface TikTokActions {
    fun setInput(text: String)
    fun extract(text: String? = null)
    fun download(asset: TikTokAsset)
    fun clear()
}

fun TikTokAsset.key() = when (this) { TikTokAsset.Video -> "video"; TikTokAsset.Audio -> "audio"; is TikTokAsset.Photo -> "photo:$index" }

class TikTokViewModel(private val client: TikTokClient, private val saver: MediaSaver, private val messenger: Messenger) : ViewModel(), TikTokActions {
    private val _state = MutableStateFlow(TikTokUiState(configured = client.configured))
    val state: StateFlow<TikTokUiState> = _state.asStateFlow()

    override fun setInput(text: String) = _state.update { it.copy(input = text, error = null) }

    override fun extract(text: String?) {
        val input = (text ?: _state.value.input).trim()
        if (input.isEmpty()) return
        if (TikTok.findLink(input) == null) { _state.update { it.copy(error = "Bukan link TikTok yang dikenali. Tempel link dari tombol Bagikan → Salin tautan.") }; return }
        _state.update { it.copy(input = input, loading = true, error = null, post = null, saved = emptyMap()) }
        launchSafe {
            try {
                val post = client.extract(input)
                _state.update { it.copy(loading = false, post = post, extractedAt = System.currentTimeMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    override fun download(asset: TikTokAsset) {
        val s = _state.value
        val post = s.post ?: return
        val key = asset.key()
        if (key in s.downloading) return
        _state.update { it.copy(downloading = it.downloading + (key to null)) }
        launchSafe {
            var pending: MediaSaver.Pending? = null
            try {
                val mime = when (asset) { TikTokAsset.Video -> "video/mp4"; TikTokAsset.Audio -> "audio/mpeg"; is TikTokAsset.Photo -> "image/jpeg" }
                val name = client.downloadAsset(
                    current = post to s.extractedAt, asset = asset,
                    refresh = { client.extract(_state.value.input).also { p -> _state.update { it.copy(post = p, extractedAt = System.currentTimeMillis()) } } },
                    open = { fallback -> saver.create(fallback, mime).also { pending = it }.open() },
                    onProgress = { p -> _state.update { it.copy(downloading = it.downloading + (key to p)) } },
                )
                val saved = pending!!.finish(name)
                _state.update { it.copy(saved = it.saved + (key to saved)) }
                messenger.show("Tersimpan di ${saved.location}: ${saved.name}")
            } catch (e: CancellationException) {
                pending?.abort(); throw e
            } catch (e: Exception) {
                pending?.abort()
                messenger.show(e.message ?: "Gagal mengunduh")
            } finally {
                _state.update { it.copy(downloading = it.downloading - key) }
            }
        }
    }

    override fun clear() = _state.update { TikTokUiState(configured = it.configured) }
}

