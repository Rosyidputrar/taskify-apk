package com.rosyidputrar.taskify

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.UiErrors
import com.rosyidputrar.taskify.core.media.MediaSaver
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.youtube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.TikTokClient
import com.rosyidputrar.taskify.core.media.YouTubeMusicClient
import com.rosyidputrar.taskify.core.media.lyrics.LyricsRepository
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import com.rosyidputrar.taskify.core.media.youtube.VisitorDataBootstrap
import com.rosyidputrar.taskify.core.media.youtube.YouTubeAuthStore
import com.rosyidputrar.taskify.core.auth.NetworkMonitor
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.feature.media.OnlineFlow
import com.rosyidputrar.taskify.feature.media.TikTokConfig
import com.rosyidputrar.taskify.feature.media.mediaFeatureModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.check.checkModules
import org.robolectric.annotation.Config

/**
 * Tahap 9 rewrite musik: graf DI Koin untuk musik harus bisa di-resolve.
 *
 * Gap Tahap 7 yang ditutup tes ini: binding `YouTubeMusicClient` (pengganti
 * `MusicClient` Worker) belum pernah diverifikasi runtime — tidak ada tes
 * `checkModules` di repo. `checkModules` membuat satu instance tiap
 * definisi, jadi kegagalan di sini berarti layar Musik akan crash saat
 * dibuka.
 *
 * KENAPA CAKUPANNYA SUBGRAF MUSIK, BUKAN SELURUH GRAF (verifikasi
 * 2026-11-17, tiga kali percobaan `checkModules` atas `appModules()`):
 * graf aplikasi sengaja terbelah — `serverModule` (yang menyediakan
 * `AuthRepository`, `SupabaseClient`, `FilesApi`, `AttachmentManager`)
 * hanya dimuat bila `serverConfig.isComplete`, dan di tes unit field
 * server di BuildConfig kosong. Akibatnya:
 * - `authFeatureModule` gagal: `NoDefinitionFoundException:
 *   AuthRepository` (`AuthModule.kt:18`);
 * - `attachmentsFeatureModule` + `vaultFeatureModule` gagal:
 *   `AttachmentManager` tidak ada;
 * - bahkan `deviceModule` sendiri gagal: `ShareViewModel`
 *   (`AppModule.kt:532`) butuh `AttachmentManager`.
 * Ketiganya pre-existing, di luar musik, dan tidak bisa dipalsukan tanpa
 * `SupabaseClient` (tidak ada library mock di tes; `AuthRepository` /
 * `AttachmentManager` bukan `open`). Graf penuh hanya bisa
 * di-`checkModules` di environment yang server-nya terkonfigurasi —
 * bukti runtime-nya adalah smoke test layar Musik di HP (item 0
 * HP-TEST-CHECKLIST).
 *
 * Modul di bawah ini MENYALIN VERBATIM pabrik musik dari `deviceModule`
 * di `AppModule.kt` (bagian "Musik direct") beserta tiga dependensi kecil
 * yang mereka butuhkan (`APP_SCOPE`, `Messenger`, `NetworkMonitor`).
 * Bila wiring musik di `AppModule.kt` berubah, salinan ini HARUS ikut
 * diubah — itu harga dari `deviceModule` yang `private`. Yang diverifikasi
 * tetap konstruktor + dependensi ASLI tiap binding musik, bukan tiruan.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KoinGraphTest {

    @Test
    fun musicGraphResolves() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        koinApplication {
            androidContext(app)
            modules(musicModule, mediaFeatureModule)
        }.checkModules()
    }

    private companion object {
        private val appScopeQualifier = named("appScope")

        /**
         * Salinan verbatim pabrik musik `deviceModule` (`AppModule.kt`).
         * Jangan ubah isinya tanpa mengubah `AppModule.kt` juga.
         */
        private val musicModule = module {
            single(appScopeQualifier) { CoroutineScope(SupervisorJob() + Dispatchers.Default + UiErrors.handler) }
            single { Messenger() }
            single { NetworkMonitor(androidContext()) }
            single { DirectStreamResolver() }
            single { SponsorBlockRepository() }
            single { LyricsRepository(youTube = get()) }
            single {
                YouTubeMusicClient(get(), get(), get()) { get<YouTubeAuthStore>().loadAuthState() }
            }
            single {
                MusicPlayer(androidContext(), get()).also { player ->
                    get<CoroutineScope>(appScopeQualifier).launch {
                        player.notice.filterNotNull().collect { get<Messenger>().show(it) }
                    }
                }
            }
            single { YouTube() }
            single { YouTubeAuthStore(androidContext()) }
            single { VisitorDataBootstrap(get(), get()) }
            single { TikTokClient(AppConfig.tiktokApiUrl, AppConfig.publicWebOrigin) }
            single { TikTokConfig(AppConfig.publicWebOrigin) }
            single { MediaSaver(androidContext()) }
            single { OnlineFlow { get<NetworkMonitor>().online } }
        }
    }
}
