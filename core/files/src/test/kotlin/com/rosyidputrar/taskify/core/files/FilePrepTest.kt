package com.rosyidputrar.taskify.core.files

import com.rosyidputrar.taskify.core.domain.files.summarizeZip
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePrepTest {
    private val dir = createTempDirectory("taskify").toFile()

    @Test fun sha256MatchesWebCrypto() {
        val f = File(dir, "a.txt").apply { writeText("taskify") }
        // echo -n taskify | sha256sum
        assertEquals("b8e628db5d12e56dddf6c034f212ff3a1babc3ad1b45ade29089c1c886c3b0a5", FilePrep.sha256(f))
        assertEquals(FilePrep.sha256(f), FilePrep.sha256(File(dir, "b.txt").apply { writeText("taskify") }))
    }

    @Test fun zipReaderListsAndReads() {
        val zip = File(dir, "p.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("app/")); z.closeEntry()
            z.putNextEntry(ZipEntry("app/README.md")); z.write("# Halo".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("app/.env")); z.write("KEY=1".toByteArray()); z.closeEntry()
        }
        val entries = ZipReader.entries(zip)
        assertEquals(3, entries.size)
        assertTrue(entries.first { it.path == "app/" }.dir)
        assertEquals("# Halo" to false, ZipReader.readText(zip, "app/README.md"))
        assertEquals(1, summarizeZip(entries).warnings.size)
        val out = File(dir, "out.env")
        assertTrue(ZipReader.extract(zip, "app/.env", out))
        assertEquals("KEY=1", out.readText())
        assertEquals("application/zip", FilePrep.guessMime("x.ZIP"))
    }
}
