package com.rosyidputrar.taskify.core.media

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.MusicSettings
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** `MusicSettings` tanpa `forceMp3`/`archive`: kunci lama diabaikan (baca toleran) dan dibersihkan saat menyimpan. */
@RunWith(AndroidJUnit4::class)
class MusicPrefsTest {
    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun rawPrefs() = context().getSharedPreferences("music", android.content.Context.MODE_PRIVATE)

    @Test fun legacyKeysAreIgnoredWhenReading() {
        rawPrefs().edit().clear().commit()
        rawPrefs().edit().putBoolean("forceMp3", true).putBoolean("archive", false)
            .putString("quality", "low").apply()
        val settings = MusicPrefs(context()).settings
        assertEquals(MusicQuality.Low, settings.quality)
        assertEquals(MusicSettings(quality = MusicQuality.Low), settings)
    }

    @Test fun savingSettingsRemovesLegacyKeys() {
        rawPrefs().edit().clear().commit()
        rawPrefs().edit().putBoolean("forceMp3", true).putBoolean("archive", true).apply()
        val prefs = MusicPrefs(context())
        prefs.settings = MusicSettings(quality = MusicQuality.High, normalize = false, sponsorBlock = true)
        assertFalse(rawPrefs().contains("forceMp3"), "kunci lama dibersihkan saat pengaturan baru disimpan")
        assertFalse(rawPrefs().contains("archive"))
        assertEquals(MusicQuality.High, prefs.settings.quality)
        assertEquals(false, prefs.settings.normalize)
    }
}
