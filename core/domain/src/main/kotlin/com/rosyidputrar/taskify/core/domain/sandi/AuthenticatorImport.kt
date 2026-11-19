package com.rosyidputrar.taskify.core.domain.sandi

import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** Satu akun 2FA dari aplikasi authenticator lain. */
data class AuthAccount(val issuer: String, val account: String, val config: TotpConfig) {
    /** Label tampilan "Penerbit (akun)". */
    val label: String get() = authLabel(issuer, account)

    override fun toString() = "AuthAccount(issuer=$issuer, account=$account, config=•••)"
}

internal fun authLabel(issuer: String, account: String) = when {
    issuer.isNotEmpty() && account.isNotEmpty() -> "$issuer ($account)"
    else -> issuer.ifEmpty { account.ifEmpty { "Akun 2FA" } }
}

/** Satu QR / teks yang sudah dibaca. [batchId] null = bukan bagian dari ekspor bertahap. */
data class AuthScan(
    val accounts: List<AuthAccount>,
    /** (label, alasan) akun yang tidak bisa dipakai Taskify. */
    val unsupported: List<Pair<String, String>>,
    val batchId: Long? = null,
    val batchIndex: Int = 0,
    val batchSize: Int = 1,
)

/** Rencana impor 2FA terhadap isi brankas. */
data class AuthImportPlan(
    /** Item baru (hanya 2FA). */
    val add: List<ItemPayload>,
    /** Kunci 2FA dipasang ke sandi yang sudah ada (indeks ke daftar existing, item baru). */
    val attach: List<Pair<Int, ItemPayload>>,
    /** Kunci yang sama persis sudah ada di brankas. */
    val identical: Int,
    /** Akun yang sama dipindai lebih dari sekali. */
    val duplicates: Int,
)

/**
 * Impor 2FA dari Google Authenticator ("Transfer akun" → QR `otpauth-migration://offline?data=…`)
 * dan URI `otpauth://totp/…` biasa. Pembaca protobuf ditulis tahan banting: kolom tak dikenal
 * dilewati, panjang dicek, data rusak = [ImportException] (tidak pernah crash / membaca di luar batas).
 */
object AuthenticatorImport {
    private const val MAX_TEXT = 16 * 1024

    /** Baca satu hasil pindai / teks tempelan (boleh beberapa URI, satu per baris). */
    fun parse(text: String): List<AuthScan> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw ImportException("Tidak ada teks QR.")
        if (trimmed.length > MAX_TEXT) throw ImportException("Teks terlalu panjang untuk QR authenticator.")
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scans = parts.map { part ->
            when {
                part.startsWith("otpauth-migration:", ignoreCase = true) -> parseMigration(part)
                part.startsWith("otpauth:", ignoreCase = true) -> parseOtpauth(part)
                else -> throw ImportException("Ini bukan QR authenticator. Pindai QR dari menu \"Transfer akun\" Google Authenticator.")
            }
        }
        return scans
    }

    private fun parseOtpauth(uri: String): AuthScan {
        if (!Regex("^otpauth://totp/", RegexOption.IGNORE_CASE).containsMatchIn(uri)) {
            return AuthScan(emptyList(), listOf("otpauth" to "hanya TOTP (berbasis waktu) yang didukung"))
        }
        val input = Totp.parseInput(uri) ?: throw ImportException("URI otpauth:// tidak bisa dibaca atau kuncinya tidak valid.")
        return AuthScan(listOf(AuthAccount(input.name, input.username, input.config)), emptyList())
    }

    /** `otpauth-migration://offline?data=<base64 MigrationPayload>`. */
    fun parseMigration(uri: String): AuthScan {
        val u = try { URI(uri.trim()) } catch (_: Exception) { throw ImportException("QR transfer akun tidak bisa dibaca.") }
        if (!u.scheme.equals("otpauth-migration", true) || !u.host.equals("offline", true)) throw ImportException("QR transfer akun tidak dikenali.")
        val raw = (u.rawQuery ?: "").split('&').firstOrNull { it.startsWith("data=") }?.removePrefix("data=")
            ?: throw ImportException("QR transfer akun tidak berisi data.")
        // URLDecoder mengubah "+" jadi spasi: kembalikan ke "+" (base64), lalu terima base64 biasa maupun URL-safe.
        val b64 = URLDecoder.decode(raw, "UTF-8").replace(' ', '+').replace('-', '+').replace('_', '/')
        val bytes = try { Base64.getDecoder().decode(b64.padEnd((b64.length + 3) / 4 * 4, '=')) } catch (_: IllegalArgumentException) {
            throw ImportException("QR transfer akun rusak (base64 tidak valid).")
        }
        return try { decodePayload(bytes) } finally { bytes.fill(0) }
    }

    // ------------------------------------------------------------------ protobuf

    private class Reader(private val b: ByteArray, var pos: Int, private val end: Int) {
        val done get() = pos >= end

        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (pos >= end || shift > 63) throw ImportException("QR transfer akun rusak.")
                val x = b[pos++].toInt() and 0xff
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }

        fun bytes(): ByteArray {
            val n = varint()
            if (n < 0 || n > end - pos) throw ImportException("QR transfer akun rusak.")
            return b.copyOfRange(pos, pos + n.toInt()).also { pos += n.toInt() }
        }

        fun sub(): Reader {
            val n = varint()
            if (n < 0 || n > end - pos) throw ImportException("QR transfer akun rusak.")
            return Reader(b, pos, pos + n.toInt()).also { pos += n.toInt() }
        }

        /** Lewati kolom tak dikenal sesuai wire type. */
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> advance(8)
                2 -> { val n = varint(); if (n < 0 || n > end - pos) throw ImportException("QR transfer akun rusak."); pos += n.toInt() }
                5 -> advance(4)
                else -> throw ImportException("QR transfer akun rusak.")
            }
        }

        private fun advance(n: Int) {
            if (n > end - pos) throw ImportException("QR transfer akun rusak.")
            pos += n
        }
    }

    private fun decodePayload(bytes: ByteArray): AuthScan {
        val r = Reader(bytes, 0, bytes.size)
        val accounts = mutableListOf<AuthAccount>()
        val unsupported = mutableListOf<Pair<String, String>>()
        var batchSize = 1L
        var batchIndex = 0L
        var batchId: Long? = null
        while (!r.done) {
            val tag = r.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 2 -> decodeAccount(r.sub()).fold({ accounts += it }, { unsupported += it })
                field == 3 && wire == 0 -> batchSize = r.varint()
                field == 4 && wire == 0 -> batchIndex = r.varint()
                field == 5 && wire == 0 -> batchId = r.varint()
                else -> r.skip(wire) // termasuk version (2)
            }
        }
        if (accounts.isEmpty() && unsupported.isEmpty()) throw ImportException("QR transfer akun kosong.")
        val size = batchSize.coerceIn(1, 1000).toInt()
        return AuthScan(accounts, unsupported, batchId, batchIndex.coerceIn(0, size - 1L).toInt(), size)
    }

    /** Left = akun siap pakai; Right = (label, alasan tidak didukung). */
    private sealed interface Decoded {
        fun fold(ok: (AuthAccount) -> Unit, bad: (Pair<String, String>) -> Unit)
    }
    private class Ok(val a: AuthAccount) : Decoded { override fun fold(ok: (AuthAccount) -> Unit, bad: (Pair<String, String>) -> Unit) = ok(a) }
    private class Bad(val p: Pair<String, String>) : Decoded { override fun fold(ok: (AuthAccount) -> Unit, bad: (Pair<String, String>) -> Unit) = bad(p) }

    private fun decodeAccount(r: Reader): Decoded {
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 1L
        var digits = 1L
        var type = 2L
        while (!r.done) {
            val tag = r.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 2 -> secret = r.bytes()
                field == 2 && wire == 2 -> name = String(r.bytes(), Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = String(r.bytes(), Charsets.UTF_8)
                field == 4 && wire == 0 -> algorithm = r.varint()
                field == 5 && wire == 0 -> digits = r.varint()
                field == 6 && wire == 0 -> type = r.varint()
                else -> r.skip(wire) // counter (7) dan kolom baru
            }
        }
        val (iss, acc) = splitLabel(issuer.trim(), name.trim())
        val label = authLabel(iss, acc)
        try {
            if (type == 1L) return Bad(label to "HOTP (berbasis penghitung) belum didukung")
            if (type != 0L && type != 2L) return Bad(label to "jenis kode tidak dikenal")
            val alg = when (algorithm) {
                0L, 1L -> TotpAlgorithm.SHA1
                2L -> TotpAlgorithm.SHA256
                3L -> TotpAlgorithm.SHA512
                4L -> return Bad(label to "algoritme MD5 tidak didukung")
                else -> return Bad(label to "algoritme tidak dikenal")
            }
            val d = when (digits) {
                0L, 1L -> 6
                2L -> 8
                else -> return Bad(label to "jumlah digit tidak didukung")
            }
            if (secret.size < Totp.MIN_SECRET_BYTES) return Bad(label to "kunci terlalu pendek")
            return Ok(AuthAccount(iss, acc, TotpConfig(Totp.encodeBase32(secret), d, Totp.DEFAULT_PERIOD, alg)))
        } finally {
            secret.fill(0)
        }
    }

    /** Google sering menyimpan nama "Penerbit:akun"; pisahkan supaya tidak dobel. */
    fun splitLabel(issuer: String, name: String): Pair<String, String> {
        val sep = name.indexOf(':')
        if (sep < 0) return issuer to name
        val prefix = name.substring(0, sep).trim()
        val rest = name.substring(sep + 1).trim()
        return when {
            issuer.isEmpty() -> prefix to rest
            prefix.equals(issuer, ignoreCase = true) -> issuer to rest
            else -> issuer to name
        }
    }

    // ------------------------------------------------------------------ batch

    /**
     * Status pengumpulan QR bertahap: Google memecah ekspor jadi beberapa QR dengan batch_id sama.
     * Mengembalikan indeks QR yang belum dipindai untuk setiap batch.
     */
    fun missing(scans: List<AuthScan>): List<Int> {
        val batches = scans.filter { it.batchId != null }.groupBy { it.batchId }
        return batches.values.flatMap { group ->
            val size = group.maxOf { it.batchSize }
            (0 until size).filter { i -> group.none { it.batchIndex == i } }
        }
    }

    // ------------------------------------------------------------------ rencana

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Sandi yang cocok untuk dipasangi kunci 2FA: belum punya 2FA, nama pengguna sama, situs/judul cocok penerbit. */
    fun matches(item: ItemPayload, a: AuthAccount): Boolean {
        if (item.totp != null) return false
        val user = a.account.trim()
        if (user.isEmpty() || !item.username.trim().equals(user, ignoreCase = true)) return false
        val issuer = norm(a.issuer)
        if (issuer.isEmpty()) return false
        val title = norm(item.title)
        val host = AutofillMatch.host(item.url)?.let { h -> norm(AutofillMatch.registrable(h).substringBefore('.')) }.orEmpty()
        return title == issuer || (host.isNotEmpty() && host == issuer) || (title.isNotEmpty() && (title.startsWith(issuer) || issuer.startsWith(title)) && minOf(title.length, issuer.length) >= 4)
    }

    /**
     * Rencana: akun yang sama (kunci sama) dipindai berulang dihitung sekali; kunci yang sudah ada di
     * brankas dilewati; bila [attach] aktif, kunci dipasang ke sandi yang cocok persis satu
     * (tidak ada yang ditimpa — hanya item tanpa 2FA). Sisanya jadi item baru.
     */
    fun plan(scans: List<AuthScan>, existing: List<ItemPayload>, attach: Boolean): AuthImportPlan {
        val known = existing.mapNotNull { it.totp?.secret }.toHashSet()
        val seen = HashSet<String>()
        val claimed = HashSet<Int>()
        val add = mutableListOf<ItemPayload>()
        val attachTo = mutableListOf<Pair<Int, ItemPayload>>()
        var identical = 0
        var duplicates = 0
        scans.flatMap { it.accounts }.forEach { a ->
            if (!seen.add(a.config.secret)) { duplicates++; return@forEach }
            if (a.config.secret in known) { identical++; return@forEach }
            if (attach) {
                val candidates = existing.indices.filter { it !in claimed && matches(existing[it], a) }
                if (candidates.size == 1) {
                    val i = candidates.single()
                    claimed += i
                    attachTo += i to existing[i].copy(totp = a.config)
                    return@forEach
                }
            }
            add += ItemPayload(title = a.issuer.ifEmpty { a.account.ifEmpty { "Akun 2FA" } }, username = a.account, totp = a.config)
        }
        return AuthImportPlan(add, attachTo, identical, duplicates)
    }
}
