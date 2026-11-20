package com.rosyidputrar.taskify.feature.media

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jalur salin detail mentah (Agen A): SELURUH teks mentah masuk clipboard
 * apa adanya. Ketukan tombolnya sendiri tidak diuji di sini (butuh
 * Compose test); yang dibuktikan adalah fungsi yang dipanggil tombol itu.
 */
@RunWith(AndroidJUnit4::class)
class RawErrorDetailCopyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val detail = "videoId: dQw4w9WgXcQ\nformat: itag 251 (audio/webm; codecs=\"opus\")\n" +
        "host: rr2---sn-a5mekn7z.googlevideo.com\nerrorCodeName: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED\n" +
        "errorCode: 1002001\nmessage: Gagal tersambung."

    @Test fun copyWritesWholeDetailToClipboard() {
        assertTrue(copyRawDetailToClipboard(context, detail))
        val clipboard = context.getSystemService(ClipboardManager::class.java)!!
        val clip = clipboard.primaryClip!!
        assertEquals(1, clip.itemCount)
        assertEquals(detail, clip.getItemAt(0).coerceToText(context).toString())
    }

    @Test fun copyReplacesPreviousClip() {
        copyRawDetailToClipboard(context, "lama")
        copyRawDetailToClipboard(context, detail)
        val clipboard = context.getSystemService(ClipboardManager::class.java)!!
        assertEquals(detail, clipboard.primaryClip!!.getItemAt(0).coerceToText(context).toString())
    }
}
