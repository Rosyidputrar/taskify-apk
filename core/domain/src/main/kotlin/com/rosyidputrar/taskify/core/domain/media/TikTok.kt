package com.rosyidputrar.taskify.core.domain.media

import java.net.URI
import java.net.URLDecoder

/** Jawaban `POST /v1/extract` Worker ttdl-taskify (URL unduhan = link Worker, bukan CDN). */
data class TikTokPost(
    val id: String,
    val type: String,
    val description: String,
    val createdAt: Long,
    val canonicalUrl: String,
    val authorId: String,
    val authorName: String,
    val verified: Boolean,
    val plays: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val durationSeconds: Int?,
    val musicTitle: String?,
    val musicAuthor: String?,
    val hashtags: List<String>,
    val cover: String?,
    val videoUrl: String?,
    val photoUrls: List<String>,
    val audioUrl: String?,
) {
    val isPhoto: Boolean get() = type == "photo"
}

sealed interface TikTokAsset {
    data object Video : TikTokAsset
    data object Audio : TikTokAsset
    data class Photo(val index: Int) : TikTokAsset
}

object TikTok {
    private val URL_RE = Regex("(?:https?://)?(?:[a-z0-9-]+\\.)*tiktok\\.com/[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    /** Deteksi link TikTok (postingan atau link pendek) di teks bebas; port `tiktok-link.ts`. */
    fun findLink(text: String): String? {
        val match = URL_RE.find(text) ?: return null
        var candidate = match.value.replace(Regex("[.,;:!?)\\]]+$"), "")
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(candidate)) candidate = "https://$candidate"
        return try {
            val url = URI(candidate)
            val host = url.host?.lowercase() ?: return null
            if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return null
            val path = url.rawPath ?: ""
            val isPost = Regex("/(video|photo)/\\d{17,21}").containsMatchIn(path)
            val isShort = (Regex("^(vt|vm)\\.").containsMatchIn(host) && Regex("^/[\\w-]+").containsMatchIn(path)) || Regex("^/t/[\\w-]+").containsMatchIn(path)
            if (isPost || isShort) url.toString().let { if (path.isEmpty()) "$it/" else it } else null
        } catch (_: Exception) {
            null
        }
    }

    /** `attachment; filename="a.mp4"; filename*=UTF-8''a.mp4` → `a.mp4`. */
    fun fileNameFromDisposition(header: String?, fallback: String): String {
        if (header == null) return fallback
        var name = ""
        Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            name = runCatching { URLDecoder.decode(m.groupValues[1].trim().replace("+", "%2B"), "UTF-8") }.getOrDefault("")
        }
        if (name.isEmpty()) {
            val plain = Regex("filename\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(header) ?: Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)
            name = plain?.groupValues?.get(1)?.trim().orEmpty()
        }
        val clean = name.replace(Regex("[/\\\\]"), "_").filter { it.code >= 0x20 }
        return clean.ifEmpty { fallback }
    }

    fun assetUrl(post: TikTokPost, asset: TikTokAsset): String? = when (asset) {
        TikTokAsset.Video -> post.videoUrl
        TikTokAsset.Audio -> post.audioUrl
        is TikTokAsset.Photo -> post.photoUrls.getOrNull(asset.index)
    }

    fun fallbackName(post: TikTokPost, asset: TikTokAsset): String {
        val base = "${post.authorId.ifEmpty { "tiktok" }}_${post.id}"
        return when (asset) {
            TikTokAsset.Video -> "$base.mp4"
            TikTokAsset.Audio -> "${base}_audio.mp3"
            is TikTokAsset.Photo -> "${base}_${(asset.index + 1).toString().padStart(2, '0')}.jpg"
        }
    }

    /** Link Worker berlaku 10 menit; diperbarui lebih awal supaya tidak mepet. */
    const val LINK_MAX_AGE_MS = 8 * 60_000L

    /** Kode galat yang berarti link perlu dibuat ulang (bukan kegagalan permanen). */
    fun isStale(code: String?) = code == "TOKEN_EXPIRED" || code == "TOKEN_INVALID" || code == "UPSTREAM"

    /** 1234567 → "1,2 jt"; 15300 → "15,3 rb" (statistik kartu). */
    fun compactCount(n: Long): String = when {
        n >= 1_000_000 -> "%.1f jt".format(n / 1_000_000.0).replace('.', ',').replace(",0 ", " ")
        n >= 1_000 -> "%.1f rb".format(n / 1_000.0).replace('.', ',').replace(",0 ", " ")
        else -> n.toString()
    }
}
