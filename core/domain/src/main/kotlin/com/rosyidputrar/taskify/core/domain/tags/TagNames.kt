package com.rosyidputrar.taskify.core.domain.tags

/** "#Kerja Penting" → "kerja-penting" (huruf kecil, tanpa spasi, maks. 50) — sama dengan webapp. */
fun normalizeTagName(raw: String): String = raw.trim()
    .trimStart('#')
    .lowercase()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("[^\\p{L}\\p{N}_-]"), "")
    .take(50)

/** Nama warna yang disimpan di DB (task_lists.color, tags.color, …) — urutan sama dengan webapp. */
val PALETTE_NAMES = listOf("violet", "indigo", "sky", "teal", "emerald", "amber", "orange", "red", "rose", "pink", "fuchsia", "slate")
