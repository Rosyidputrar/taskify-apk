package com.rosyidputrar.taskify.core.media.youtube

/**
 * Diagnostik URL stream untuk kasus 403 googlevideo — HANYA nama parameter,
 * tidak pernah nilai (query URL berisi signature/IP; nilainya rahasia).
 *
 * Dipakai dua tempat: log resolve di [com.rosyidputrar.taskify.core.media.MusicPlayer]
 * dan detail mentah error pemutar. Murni (tanpa Android), supaya bisa diuji
 * di JVM biasa.
 */

/** Nama-nama parameter query di [url], huruf kecil; kosong bila URL tidak punya query. */
fun streamUrlQueryParamNames(url: String): Set<String> {
    val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    if (query.isBlank()) return emptySet()
    return query.split('&')
        .map { it.substringBefore('=').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()
}

/**
 * Keberadaan parameter yang menentukan diagnosis 403, sebagai teks
 * `"n: ada, pot: tidak ada, sig: ada, expire: ada"`. `null`/kosong →
 * `"tidak diketahui"`. TIDAK PERNAH memuat nilai parameter.
 */
fun streamUrlParamPresence(url: String?): String {
    if (url.isNullOrBlank()) return "tidak diketahui"
    val names = streamUrlQueryParamNames(url)
    fun ada(nama: String) = if (nama in names) "ada" else "tidak ada"
    return "n: ${ada("n")}, pot: ${ada("pot")}, sig: ${ada("sig")}, expire: ${ada("expire")}"
}
