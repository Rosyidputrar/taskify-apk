package com.rosyidputrar.taskify.core.common

import java.security.MessageDigest

object Hashes {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * 16 hex pertama SHA-256 (teks UTF-8). SAMA dengan `sha256Hex16` webapp dan
     * `public.vault_key_ref()` di Postgres: label kunci brankas & sidik versi baris.
     */
    fun sha256Hex16(text: String): String = hex(sha256(text.toByteArray(Charsets.UTF_8)).copyOf(8))
}
