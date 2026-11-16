package com.rosyidputrar.taskify.core.domain.auth

import com.rosyidputrar.taskify.core.common.Limits

/**
 * Rapikan nama tampilan: spasi/baris baru jadi satu spasi, buang karakter kontrol, potong ke
 * batas. Karakter format (ZWJ/ZWNJ) sengaja dipertahankan — dipakai aksara tertentu & emoji.
 */
fun cleanDisplayName(raw: String?): String {
    val collapsed = (raw ?: "")
        .replace(Regex("\\s+"), " ")
        .filterNot { Character.getType(it) == Character.CONTROL.toInt() }
        .trim()
    return Limits.clip(collapsed, Limits.DISPLAY_NAME).trimEnd()
}

/** Sapaan layar masuk. */
fun loginGreeting(name: String?): String =
    if (name.isNullOrBlank()) "Selamat datang di Taskify" else "Selamat datang kembali, $name"

fun homeGreeting(greeting: String, name: String?): String =
    if (name.isNullOrBlank()) greeting else "$greeting, $name"
