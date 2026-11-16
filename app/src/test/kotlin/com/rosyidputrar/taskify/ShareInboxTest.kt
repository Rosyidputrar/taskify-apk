package com.rosyidputrar.taskify

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.ui.share.ShareInbox
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Penerima "Bagikan ke Taskify" tidak boleh menyalin berkas privat aplikasi sendiri atau berkas tanpa batas. */
@RunWith(AndroidJUnit4::class)
class ShareInboxTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Provider "aplikasi lain" yang menyajikan berkas berukuran tertentu. */
    class OtherAppProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val size = uri.getQueryParameter("size")!!.toInt()
            val f = File.createTempFile("share", ".bin").apply { writeBytes(ByteArray(size) { 7 }) }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?) = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    @Before fun setUp() {
        Robolectric.setupContentProvider(OtherAppProvider::class.java, OTHER)
    }

    private fun other(name: String, size: Int) = Uri.parse("content://$OTHER/$name?size=$size")

    private fun send(vararg uris: Uri) = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.toList()))

    @Test fun copiesFilesFromOtherApps() = runBlocking {
        val inbox = ShareInbox(context)
        assertTrue(inbox.receive(send(other("foto.jpg", 1_000))))
        val files = inbox.content.value!!.files
        assertEquals(listOf("foto.jpg"), files.map { it.name })
        assertEquals(1_000L, files.single().size)
        assertEquals(0, inbox.content.value!!.skipped)
    }

    @Test fun rejectsPrivateFilesAndOwnProvider() = runBlocking {
        // Berkas privat Taskify (mis. database / token sesi).
        val secret = File(context.filesDir, "rahasia.db").apply { writeText("token") }
        val inbox = ShareInbox(context)
        val accepted = inbox.receive(
            send(
                Uri.fromFile(secret),
                Uri.parse("content://${context.packageName}.files/attachments/rahasia.db"),
            ),
        )
        assertFalse(accepted)
        assertNull(inbox.content.value)
        assertTrue(File(context.cacheDir, "share").listFiles().orEmpty().isEmpty(), "tidak ada salinan di cache")
    }

    @Test fun enforcesSizeAndCountLimits() = runBlocking {
        val inbox = ShareInbox(context, maxFileBytes = 1_000, maxFiles = 2, maxTotalBytes = 1_500)
        inbox.receive(send(other("besar.bin", 1_001), other("a.bin", 800), other("b.bin", 800), other("c.bin", 10)))
        val content = inbox.content.value!!
        // besar.bin > batas per berkas; b.bin melampaui total; c.bin masuk (2 berkas maks).
        assertEquals(listOf("a.bin", "c.bin"), content.files.map { it.name })
        assertEquals(2, content.skipped)
        // Salinan parsial yang ditolak tidak tertinggal di cache.
        assertEquals(2, File(context.cacheDir, "share").listFiles().orEmpty().size)
    }

    @Test fun textOnlyShareStillWorks() = runBlocking {
        val inbox = ShareInbox(context)
        assertTrue(inbox.receive(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "Beli susu")))
        assertEquals("Beli susu", inbox.content.value!!.merged)
    }

    private companion object {
        const val OTHER = "com.aplikasi.lain.files"
    }
}
