package com.rosyidputrar.taskify.core.common

/**
 * Batas panjang teks yang ditegakkan CHECK Postgres. Semua jalur tulis memotong lewat [clip]
 * supaya baris tidak ditolak server (23514) dan berakhir di `upload_errors`.
 */
object Limits {
    const val TASK_TITLE = 300
    const val JOURNAL_TITLE = 200
    const val NOTE_TITLE = 200
    const val TRANSACTION_TITLE = 120
    const val RECURRING_TITLE = 120
    const val SNIPPET_TITLE = 200
    const val PROJECT_NAME = 120
    const val COUNTERPARTY = 100
    const val CONFIG_NAME = 120
    const val CONFIG_ENVIRONMENT = 40

    /** Hanya batas klien: `user_settings.display_name` tidak punya CHECK panjang. */
    const val DISPLAY_NAME = 60

    /**
     * Potong ke [max] KARAKTER (code point, sama dengan `char_length` Postgres), tidak pernah
     * membelah pasangan surrogate (emoji).
     */
    fun clip(value: String, max: Int): String {
        if (value.length <= max) return value
        val count = value.codePointCount(0, value.length)
        if (count <= max) return value
        return value.substring(0, value.offsetByCodePoints(0, max))
    }
}
