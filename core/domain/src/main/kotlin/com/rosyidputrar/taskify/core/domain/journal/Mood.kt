package com.rosyidputrar.taskify.core.domain.journal

/** Mood jurnal skala 1–5 (label sama dengan webapp). */
val MOOD_LABELS = listOf("Sangat buruk", "Buruk", "Biasa", "Baik", "Sangat baik")

fun moodLabel(value: Number?): String? {
    val v = value?.toDouble() ?: return null
    val rounded = Math.round(v).toInt()
    return MOOD_LABELS.getOrNull(rounded - 1)
}
