package com.rosyidputrar.taskify.core.common

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 (RFC 9562) dibuat di perangkat: bisa membuat data saat offline dan tetap terurut waktu.
 * Format sama dengan `newId()` di webapp, sehingga baris dari HP & laptop konsisten.
 */
object Ids {
    private val random = SecureRandom()
    private var lastMillis = 0L
    private var counter = 0

    @Synchronized
    fun newId(nowMillis: Long = System.currentTimeMillis()): String {
        // Monoton dalam satu proses: id yang dibuat pada milidetik sama tetap terurut.
        val millis = if (nowMillis <= lastMillis) lastMillis else nowMillis
        counter = if (millis == lastMillis) counter + 1 else random.nextInt(1 shl 11)
        if (counter >= (1 shl 12)) {
            lastMillis = millis + 1
            counter = 0
        } else {
            lastMillis = millis
        }
        val msb = (lastMillis shl 16) or (0x7L shl 12) or counter.toLong()
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }

    fun isUuid(value: String): Boolean =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(value)
}
