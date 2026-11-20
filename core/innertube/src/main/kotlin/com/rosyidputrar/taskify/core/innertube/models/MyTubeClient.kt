/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `core/.../innertube/models/MyTubeClient.kt`
// (v15.1.0, c7f0e24) — paket diubah ke Taskify, ditambah aksesori ANDROID
// (profil ANDROID disamakan dengan MOBILE ArchiveTune 21.26.364 — UA aplikasi
// asli, bukan UA desktop Chrome 131 (porting ArchiveTune Tahap 1, gap 2).
// Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.innertube.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class MyTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val supportsCookieAuthentication: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val useWebPoTokens: Boolean = false,
    val isEmbedded: Boolean = false,
) {
    fun toContext(
        locale: MyTubeLocale,
        visitorData: String?,
        dataSyncId: String?,
    ) = Context(
        client =
            Context.Client(
                clientName = clientName,
                clientVersion = clientVersion,
                osName = osName,
                osVersion = osVersion,
                deviceMake = deviceMake,
                deviceModel = deviceModel,
                androidSdkVersion = androidSdkVersion,
                gl = locale.gl,
                hl = locale.hl,
                visitorData = visitorData,
            ),
        user =
            Context.User(
                onBehalfOfUser = if (supportsCookieAuthentication) dataSyncId.delegatedSessionIdOrNull() else null,
            ),
        // Klien embedded (ArchiveTune `InnerTube.kt:344-395`, C2-7):
        // `thirdParty.embedUrl` wajib ada bila klien embedded; klien
        // biasa tidak membawa field ini.
        thirdParty = if (isEmbedded) Context.ThirdParty("https://www.reddit.com/") else null,
    )

    fun requestOrigin(): String =
        when (clientName.uppercase(Locale.US)) {
            "WEB_REMIX" -> ORIGIN_MYTUBE_MUSIC
            "MWEB" -> ORIGIN_MYTUBE_MOBILE
            else -> ORIGIN_MYTUBE
        }

    fun requestReferer(): String =
        when (clientName.uppercase(Locale.US)) {
            "WEB_REMIX" -> REFERER_MYTUBE_MUSIC
            "MWEB" -> REFERER_MYTUBE_MOBILE
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY" -> REFERER_MYTUBE_TV
            else -> REFERER_MYTUBE
        }

    fun requestApiUrl(endpoint: String): String = "${requestOrigin()}/youtubei/v1/$endpoint"

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        const val USER_AGENT_WEB_REMIX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_MYTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_MYTUBE_MUSIC = "$ORIGIN_MYTUBE_MUSIC/"
        const val API_URL_MYTUBE_MUSIC = "$ORIGIN_MYTUBE_MUSIC/youtubei/v1/"

        const val ORIGIN_MYTUBE = "https://www.youtube.com"
        const val REFERER_MYTUBE = "$ORIGIN_MYTUBE/"
        const val ORIGIN_MYTUBE_MOBILE = "https://m.youtube.com"
        const val REFERER_MYTUBE_MOBILE = "$ORIGIN_MYTUBE_MOBILE/"
        const val REFERER_MYTUBE_TV = "$ORIGIN_MYTUBE/tv"

        val WEB: MyTubeClient
            get() = profile("WEB")

        val WEB_PRIMARY: MyTubeClient
            get() = profile("WEB_PRIMARY")

        val WEB_REMIX: MyTubeClient
            get() = profile("WEB_REMIX")

        val WEB_CREATOR: MyTubeClient
            get() = profile("WEB_CREATOR")

        val TVHTML5: MyTubeClient
            get() = profile("TVHTML5")

        val TVHTML5_DOWNGRADED: MyTubeClient
            get() = profile("TVHTML5_DOWNGRADED")

        val TVHTML5_SIMPLY_EMBEDDED_PLAYER: MyTubeClient
            get() = profile("TVHTML5_SIMPLY_EMBEDDED_PLAYER")

        val TVHTML5_SIMPLY: MyTubeClient
            get() = profile("TVHTML5_SIMPLY")

        val IOS: MyTubeClient
            get() = profile("IOS")

        val MOBILE: MyTubeClient
            get() = profile("MOBILE")

        val ANDROID_VR_NO_AUTH: MyTubeClient
            get() = profile("ANDROID_VR_NO_AUTH")

        val ANDROID_VR_1_65_10: MyTubeClient
            get() = profile("ANDROID_VR_1_65_10")

        val ANDROID_VR_1_61_48: MyTubeClient
            get() = profile("ANDROID_VR_1_61_48")

        val ANDROID_VR_1_43_32: MyTubeClient
            get() = profile("ANDROID_VR_1_43_32")

        val ANDROID_CREATOR: MyTubeClient
            get() = profile("ANDROID_CREATOR")

        val VISIONOS: MyTubeClient
            get() = profile("VISIONOS")

        val IPADOS: MyTubeClient
            get() = profile("IPADOS")

        val MWEB: MyTubeClient
            get() = profile("MWEB")

        val WEB_SAFARI: MyTubeClient
            get() = profile("WEB_SAFARI")

        val WEB_EMBEDDED: MyTubeClient
            get() = profile("WEB_EMBEDDED")

        val WEB_MUSIC: MyTubeClient
            get() = profile("WEB_MUSIC")

        val ANDROID_MUSIC: MyTubeClient
            get() = profile("ANDROID_MUSIC")

        val ANDROID_TESTSUITE: MyTubeClient
            get() = profile("ANDROID_TESTSUITE")

        val ANDROID_UNPLUGGED: MyTubeClient
            get() = profile("ANDROID_UNPLUGGED")

        val IOS_MUSIC: MyTubeClient
            get() = profile("IOS_MUSIC")

        /**
         * Klien `player` utama rewrite ini — TIDAK ADA di katalog ArchiveTune.
         * Profil ditambahkan di `youtube_clients.json`: ANDROID 21.26.364
         * (disamakan dengan MOBILE ArchiveTune), clientId "3",
         * androidSdkVersion 34 (lihat `worker/innertube.ts`
         * DIRECT_CLIENT_CONTEXT; terverifikasi live 2026-11-16: OK + 24 format).
         */
        val ANDROID: MyTubeClient
            get() = profile("ANDROID")

        private fun profile(key: String): MyTubeClient =
            clientCatalog.profiles[key]
                ?: error("Missing YouTube client profile: $key")

        private val clientCatalog: MyTubeClientCatalog by lazy(LazyThreadSafetyMode.PUBLICATION) {
            loadClientCatalog()
        }
    }
}

@Serializable
private data class MyTubeClientCatalog(
    val profiles: Map<String, MyTubeClient>,
    // Nama kunci JSON di `youtube_clients.json` tetap `youtubeOriginClientNames`
    // (nilai asal URL dikirim apa adanya); hanya nama properti Kotlin yang di-rename.
    @SerialName("youtubeOriginClientNames")
    val myTubeOriginClientNames: Set<String>,
    val upstreamClientIdentifiers: List<MyTubeClientIdentifier>,
)

@Serializable
private data class MyTubeClientIdentifier(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
)

private val clientCatalogJson = Json {
    ignoreUnknownKeys = false
}

private fun loadClientCatalog(): MyTubeClientCatalog {
    val resource =
        checkNotNull(MyTubeClient::class.java.getResourceAsStream("/innertube/youtube_clients.json")) {
            "Missing YouTube client catalog resource."
        }

    return resource.bufferedReader().use { reader ->
        clientCatalogJson.decodeFromString(reader.readText())
    }
}

private fun String?.delegatedSessionIdOrNull(): String? {
    val value = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    val separatorIndex = value.indexOf("||")
    if (separatorIndex <= 0 || separatorIndex + 2 >= value.length) return null

    return value.substring(0, separatorIndex).trim().takeIf(String::isNotBlank)
}
