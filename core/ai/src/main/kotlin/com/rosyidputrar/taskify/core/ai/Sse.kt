package com.rosyidputrar.taskify.core.ai

/** Satu kejadian Server-Sent Events. */
data class SseEvent(val event: String?, val data: String)

/**
 * Pengurai SSE bertahap (WHATWG): baris `event:` / `data:` dikumpulkan sampai baris kosong.
 * Baris komentar (`:`) dan field lain diabaikan. Toleran CRLF dan potongan baris yang terbelah.
 */
class SseParser {
    private val buffer = StringBuilder()
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Masukkan potongan teks; kembalikan kejadian yang sudah lengkap. */
    fun feed(chunk: String): List<SseEvent> {
        buffer.append(chunk)
        val out = mutableListOf<SseEvent>()
        while (true) {
            val nl = buffer.indexOf('\n')
            if (nl < 0) break
            var line = buffer.substring(0, nl)
            buffer.delete(0, nl + 1)
            if (line.endsWith('\r')) line = line.dropLast(1)
            line(line)?.let(out::add)
        }
        return out
    }

    /** Akhir aliran: kejadian terakhir tanpa baris kosong penutup tetap dikirim. */
    fun finish(): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        if (buffer.isNotEmpty()) {
            val rest = buffer.toString().removeSuffix("\r")
            buffer.clear()
            line(rest)?.let(out::add)
        }
        dispatch()?.let(out::add)
        return out
    }

    private fun line(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(':')) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val e = if (hasData) SseEvent(event, data.toString()) else null
        event = null
        data.clear()
        hasData = false
        return e
    }
}
