package com.rosyidputrar.taskify.core.common

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Timestamp ISO UTC dengan presisi milidetik, persis seperti `new Date().toISOString()` di webapp
 * (`2026-11-15T11:13:11.000Z`). Format yang sama membuat urutan teks = urutan waktu di SQLite.
 */
object Timestamps {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun nowIso(nowMillis: Long = System.currentTimeMillis()): String = format(nowMillis)

    fun format(epochMillis: Long): String = formatter.format(Instant.ofEpochMilli(epochMillis))

    fun parseMillis(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
}
