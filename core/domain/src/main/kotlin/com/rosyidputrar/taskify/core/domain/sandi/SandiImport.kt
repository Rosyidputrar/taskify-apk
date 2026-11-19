package com.rosyidputrar.taskify.core.domain.sandi

/** Format CSV yang dikenali dari header-nya. */
enum class ImportFormat(val label: String) {
    Google("Pengelola Sandi Google / Chrome"),
    Bitwarden("Bitwarden"),
    Generic("CSV"),
}

/** Satu baris CSV yang bisa dijadikan item ([line] = nomor baris di berkas, mulai 1 = header). */
data class ImportRow(val line: Int, val payload: ItemPayload)

/** Hasil membaca berkas: baris valid + yang dilewati karena kosong / tanpa sandi. */
data class ImportParse(
    val format: ImportFormat,
    val rows: List<ImportRow>,
    /** Baris tanpa sandi dan tanpa kunci 2FA (tidak ada yang bisa disimpan). */
    val noSecret: Int,
    /** Baris yang kolomnya tidak lengkap / tidak terbaca. */
    val malformed: Int,
    /** Kunci 2FA di berkas yang tidak valid (item tetap diimpor tanpa 2FA). */
    val badTotp: Int,
)

/** Rencana impor setelah dibandingkan dengan isi brankas. */
data class ImportPlan(
    val add: List<ItemPayload>,
    /** Sudah ada di brankas dengan sandi yang sama — dilewati. */
    val identical: Int,
    /** Baris kembar di dalam berkas — dilewati. */
    val duplicateInFile: Int,
    /** Akun yang sama sudah ada tapi sandinya berbeda — TETAP ditambahkan sebagai item baru (tidak menimpa). */
    val differentPassword: Int,
)

/** Galat yang pesannya siap ditampilkan. */
class ImportException(message: String) : Exception(message)

/**
 * Impor CSV sandi (ekspor Pengelola Sandi Google / Chrome, juga Bitwarden & CSV serupa).
 * Murni dan tanpa log: isi berkas berisi sandi teks biasa.
 *
 * Aturan aman: item yang sudah ada TIDAK PERNAH ditimpa. Yang identik dilewati, yang sandinya
 * berbeda ditambahkan sebagai item baru supaya tidak ada sandi yang hilang.
 */
object SandiImport {
    /** Batas wajar supaya berkas aneh tidak menghabiskan memori. */
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ROWS = 10_000

    private val NAME = setOf("name", "title", "nama")
    private val URL = setOf("url", "website", "login_uri", "uri", "origin", "web site", "alamat")
    private val USERNAME = setOf("username", "login_username", "login", "user", "user name", "email", "nama pengguna")
    private val PASSWORD = setOf("password", "login_password", "pass", "sandi")
    private val NOTE = setOf("note", "notes", "extra", "comment", "comments", "catatan")
    private val TOTP = setOf("totp", "login_totp", "otp", "otpauth", "one-time password")
    private val TYPE = setOf("type")

    // ------------------------------------------------------------------ CSV

    /**
     * CSV RFC 4180: pemisah koma, kolom berkutip boleh memuat koma/baris baru, `""` = kutip.
     * Toleran BOM, CRLF/LF/CR, dan baris kosong. Mengembalikan (nomor baris awal, kolom).
     */
    fun parseCsv(text: String): List<Pair<Int, List<String>>> {
        val src = text.removePrefix("﻿")
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var line = 1
        var rowStart = 1
        var fieldStarted = false
        var i = 0
        fun endRow() {
            row += field.toString(); field.clear()
            if (!(row.size == 1 && row[0].isEmpty())) rows += rowStart to row
            row = mutableListOf()
            fieldStarted = false
        }
        while (i < src.length) {
            val c = src[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < src.length && src[i + 1] == '"' -> { field.append('"'); i += 2 }
                    c == '"' -> { quoted = false; i++ }
                    else -> { if (c == '\n') line++; field.append(c); i++ }
                }
                continue
            }
            when (c) {
                '"' -> {
                    if (!fieldStarted && field.isEmpty()) quoted = true else field.append(c)
                    fieldStarted = true
                    i++
                }
                ',' -> { row += field.toString(); field.clear(); fieldStarted = false; i++ }
                '\r', '\n' -> {
                    endRow()
                    i += if (c == '\r' && i + 1 < src.length && src[i + 1] == '\n') 2 else 1
                    line++
                    rowStart = line
                }
                else -> { field.append(c); fieldStarted = true; i++ }
            }
        }
        if (quoted) throw ImportException("Berkas CSV terpotong: ada tanda kutip yang tidak ditutup.")
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // ------------------------------------------------------------------ baca

    /** Baca isi berkas CSV menjadi baris impor. @throws ImportException bila bukan CSV sandi. */
    fun read(text: String): ImportParse {
        if (text.length > MAX_BYTES) throw ImportException("Berkas terlalu besar (maks. 5 MB).")
        val table = parseCsv(text)
        if (table.isEmpty()) throw ImportException("Berkas kosong.")
        val header = table.first().second.map { it.trim().lowercase() }
        fun col(names: Set<String>) = header.indexOfFirst { it in names }.takeIf { it >= 0 }
        val iName = col(NAME)
        val iUrl = col(URL)
        val iUser = col(USERNAME)
        val iPass = col(PASSWORD)
        val iNote = col(NOTE)
        val iTotp = col(TOTP)
        val iType = col(TYPE)
        if (iPass == null || (iUrl == null && iName == null)) {
            throw ImportException("Ini bukan berkas sandi yang dikenali. Header harus memuat kolom password dan url (atau name), seperti ekspor Pengelola Sandi Google.")
        }
        val body = table.drop(1)
        if (body.size > MAX_ROWS) throw ImportException("Terlalu banyak baris (maks. ${"%,d".format(MAX_ROWS).replace(',', '.')}).")
        val format = when {
            header.contains("login_password") -> ImportFormat.Bitwarden
            header.containsAll(listOf("name", "url", "username", "password")) -> ImportFormat.Google
            else -> ImportFormat.Generic
        }
        val rows = mutableListOf<ImportRow>()
        var noSecret = 0
        var malformed = 0
        var badTotp = 0
        body.forEach { (line, cells) ->
            fun at(i: Int?) = i?.let { cells.getOrNull(it) } ?: ""
            // Bitwarden: hanya entri login yang punya sandi (kartu/identitas/catatan dilewati).
            if (iType != null && format == ImportFormat.Bitwarden && at(iType).isNotBlank() && at(iType).trim().lowercase() != "login") { noSecret++; return@forEach }
            // Baris terpotong yang bahkan tidak sampai ke kolom sandi.
            if (cells.size <= iPass) { if (cells.any { it.isNotBlank() }) malformed++; return@forEach }
            val password = at(iPass)
            val totpText = at(iTotp).trim()
            val totp = totpText.takeIf { it.isNotEmpty() }?.let { Totp.parseInput(it)?.config }
            if (totpText.isNotEmpty() && totp == null) badTotp++
            if (password.isEmpty() && totp == null) {
                if (cells.any { it.isNotBlank() }) noSecret++
                return@forEach
            }
            val url = importUrl(at(iUrl))
            val title = at(iName).trim().ifEmpty { titleFromUrl(url) }
            rows += ImportRow(line, ItemPayload(title = title, url = url, username = at(iUser).trim(), password = password, notes = at(iNote).trimEnd(), totp = totp))
        }
        return ImportParse(format, rows, noSecret, malformed, badTotp)
    }

    /**
     * Alamat dari berkas → alamat Taskify. Entri aplikasi Android di ekspor Google berbentuk
     * `android://<hash>@com.bca/` → `androidapp://com.bca` (format yang dipakai Isi Otomatis Sandi).
     */
    fun importUrl(raw: String): String {
        val url = raw.trim()
        val m = ANDROID.matchEntire(url) ?: return url
        return "androidapp://" + m.groupValues[1]
    }

    private val ANDROID = Regex("^android://(?:[^@]*@)?([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/?$", RegexOption.IGNORE_CASE)

    private fun titleFromUrl(url: String): String = when {
        url.startsWith("androidapp://") -> url.removePrefix("androidapp://")
        url.isBlank() -> "(tanpa judul)"
        else -> displayHost(url)
    }

    // ------------------------------------------------------------------ rencana

    /** Kunci "akun yang sama": situs/aplikasi + nama pengguna (tanpa beda huruf besar/kecil). */
    fun accountKey(p: ItemPayload): String {
        val place = AutofillMatch.host(p.url)?.removePrefix("www.")
            ?: p.url.trim().lowercase().takeIf { it.isNotEmpty() }
            ?: ("judul:" + p.title.trim().lowercase())
        return place + "|" + p.username.trim().lowercase()
    }

    /** Bandingkan dengan isi brankas [existing]; tidak ada item lama yang diubah. */
    fun plan(parse: ImportParse, existing: List<ItemPayload>): ImportPlan {
        val known = existing.groupBy(::accountKey)
        val seen = HashSet<Pair<String, String>>()
        val add = mutableListOf<ItemPayload>()
        var identical = 0
        var duplicate = 0
        var different = 0
        parse.rows.forEach { r ->
            val p = r.payload
            val key = accountKey(p)
            if (!seen.add(key to p.password)) { duplicate++; return@forEach }
            val same = known[key].orEmpty()
            when {
                same.any { it.password == p.password && (p.totp == null || it.totp?.secret == p.totp.secret) } -> identical++
                same.isNotEmpty() -> { different++; add += p }
                else -> add += p
            }
        }
        return ImportPlan(add, identical, duplicate, different)
    }
}
