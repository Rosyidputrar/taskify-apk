package com.rosyidputrar.taskify.core.domain.sandi

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Logika Isi Otomatis (Autofill) Sandi yang tidak bergantung pada Android: mencocokkan item dengan
 * aplikasi/situs yang meminta, dan mengenali kolom nama pengguna & sandi pada formulir.
 */

/** Siapa yang meminta isi otomatis: [webDomain] (dari browser) atau aplikasi [packageName]. */
data class AutofillTarget(val packageName: String, val webDomain: String? = null, val scheme: String? = null) {
    val isBrowser: Boolean get() = packageName in KNOWN_BROWSERS

    /** Domain web hanya dipercaya bila peminta adalah browser yang dikenal (anti-spoofing ViewNode.webDomain). */
    val trustedWebDomain: String? get() = if (isBrowser) webDomain else null

    /** Label untuk pengguna: domain situs (khusus browser), atau nama paket aplikasi. */
    val label: String get() = trustedWebDomain?.let { AutofillMatch.host(it) } ?: packageName

    companion object {
        /** Browser umum: tanpa domain web, permintaannya tidak dicocokkan dengan nama paket. */
        val KNOWN_BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
            "com.microsoft.emmx", "com.brave.browser", "com.opera.browser", "com.opera.mini.native",
            "com.sec.android.app.sbrowser", "com.vivaldi.browser", "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser", "org.chromium.chrome", "com.UCMobile.intl", "com.mi.globalbrowser",
        )
    }
}

/** Seberapa cocok satu item dengan [AutofillTarget]. */
enum class MatchStrength(val score: Int) { None(0), Domain(2), Exact(3) }

object AutofillMatch {
    /**
     * Sufiks dua tingkat yang lazim — CADANGAN bila daftar Public Suffix List (OkHttp) tidak bisa
     * dipakai untuk sebuah host. Jalur utama [registrable] memakai PSL OkHttp (Mozilla PSL,
     * termasuk bagian privat: github.io, vercel.app, pages.dev, dsb.); daftar ini hanya menambal
     * host yang tidak lolos parse HttpUrl. Sumber: https://publicsuffix.org/list/
     */
    private val TWO_LEVEL_SUFFIXES = setOf(
        "co.id", "ac.id", "go.id", "or.id", "web.id", "my.id", "sch.id", "net.id", "biz.id", "mil.id", "ponpes.id", "desa.id",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "ne.jp", "com.sg", "com.my", "com.br",
        "co.in", "co.kr", "com.cn", "com.tw", "com.hk", "co.nz", "com.tr", "com.mx", "com.ph", "com.vn", "co.th",
        "co.za", "com.ar", "com.pl", "com.pe", "com.eg", "com.ng", "com.sa", "com.ae",
        // Hosting/platform: tiap subdomain pengguna adalah domain sendiri (PSL, bagian privat).
        "github.io", "gitlab.io", "vercel.app", "netlify.app", "web.app", "firebaseapp.com",
        "appspot.com", "azurewebsites.net", "herokuapp.com", "pages.dev", "workers.dev",
        "cloudfront.net", "s3.amazonaws.com", "onrender.com", "fly.dev", "railway.app",
        "glitch.me", "repl.co", "supabase.co", "notion.site", "webflow.io", "wordpress.com",
        "blogspot.com", "blogspot.co.id", "github.dev", "codeberg.page", "neocities.org",
    )

    private const val ANDROID_SCHEME = "androidapp://"

    private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val PACKAGE_SEGMENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** True bila [host] adalah literal IP (v4/v6): bukan nama domain, tidak punya registrable. */
    fun isIpLiteral(host: String): Boolean {
        if (':' in host) return true // IPv6
        if (!IPV4_LITERAL.matches(host)) return false
        return host.split('.').all { it.toInt() <= 255 }
    }

    /** Skema URL ("https"/"http"/"androidapp"), null bila tidak ada. */
    fun schemeOf(url: String): String? {
        val scheme = url.trim().lowercase().substringBefore("://", "")
        return scheme.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c in "+-." } }
    }

    /** Tampilan host untuk pengguna: host berhuruf non-ASCII ditampilkan sebagai punycode (anti-homograph). */
    fun displayHost(host: String): String {
        if (host.all { it.code < 128 }) return host
        return runCatching { java.net.IDN.toASCII(host) }.getOrNull() ?: host
    }

    /** "https://Login.Example.com:443/a?b" → "login.example.com"; bukan URL/host → null. */
    fun host(url: String): String? {
        var s = url.trim().lowercase()
        if (s.isEmpty() || s.startsWith(ANDROID_SCHEME) || s.startsWith("android://")) return null
        s = s.substringAfter("://")
        // Literal IPv6 "[::1]:8080" → "::1".
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            return s.substring(1, end).takeIf { it.isNotEmpty() }
        }
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':').trimEnd('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        if (s.isEmpty() || ' ' in s || !s.contains('.')) return null
        if (s.length > 253) return null
        // Validasi label: kosong, terlalu panjang, atau diawali/diakhiri '-' → bukan host.
        if (s.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') || it.any { c -> c.isWhitespace() || c.code < 0x21 } }) return null
        return s
    }

    /**
     * Domain yang bisa didaftarkan: "a.b.tokopedia.co.id" → "tokopedia.co.id".
     * Literal IP dikembalikan apa adanya (192.168.1.1 bukan "satu domain" dengan 10.0.1.1).
     * Jalur utama memakai Public Suffix List OkHttp; fallback daftar [TWO_LEVEL_SUFFIXES].
     */
    fun registrable(host: String): String {
        if (isIpLiteral(host)) return host
        val viaPsl = runCatching { "https://$host".toHttpUrlOrNull()?.topPrivateDomain() }.getOrNull()
        if (viaPsl != null) return viaPsl
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val take = if (lastTwo in TWO_LEVEL_SUFFIXES) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    /** Paket aplikasi yang tersimpan di item: "androidapp://com.bca" / "android://com.bca". */
    fun packageOf(url: String): String? {
        val s = url.trim()
        val rest = when {
            s.startsWith(ANDROID_SCHEME, ignoreCase = true) -> s.substring(ANDROID_SCHEME.length)
            s.startsWith("android://", ignoreCase = true) -> s.substring("android://".length)
            else -> return null
        }
        val pkg = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        // '@' bukan bagian nama paket: "androidapp://com.bca@com.evil" bukan item com.bca.
        if (pkg.isBlank() || '@' in pkg) return null
        if (!pkg.split('.').all { PACKAGE_SEGMENT.matches(it) }) return null
        return pkg
    }

    /**
     * Kecocokan item dengan target.
     *
     * Web (ada [AutofillTarget.webDomain]): Exact bila host sama, Domain bila registrable sama.
     * Item https TIDAK ditawarkan ke target http (skema diketahui) — sandi https tidak boleh
     * diisi ke halaman plaintext.
     *
     * Aplikasi non-browser: HANYA ikatan eksplisit `androidapp://` yang boleh Exact. Heuristik
     * nama paket (prefix/reversed/nama, dulu: Domain/Related) DIHAPUS — paket sideload seperti
     * `com.google.evil` atau `com.tokopedia.evil` tidak boleh menerima item `google.com` /
     * `tokopedia.com` sebagai dataset langsung. Item lain tetap bisa dipilih manual di picker
     * (dengan dialog konfirmasi yang menampilkan paket peminta).
     *
     * Browser tanpa domain web (dikenal maupun tidak): tidak ada tebakan dari nama paket.
     */
    fun strength(item: ItemPayload, target: AutofillTarget): MatchStrength {
        val web = target.trustedWebDomain?.let(::host)
        if (web != null) {
            val itemHost = host(item.url) ?: return MatchStrength.None
            if (target.scheme == "http" && schemeOf(item.url) != "http") return MatchStrength.None
            return when {
                itemHost == web -> MatchStrength.Exact
                registrable(itemHost) == registrable(web) -> MatchStrength.Domain
                else -> MatchStrength.None
            }
        }
        // Permintaan aplikasi. Browser tanpa domain: jangan menebak dari nama paket browser.
        if (target.isBrowser) return MatchStrength.None
        val pkg = target.packageName.lowercase()
        packageOf(item.url)?.let { return if (it.lowercase() == pkg) MatchStrength.Exact else MatchStrength.None }
        return MatchStrength.None
    }

    /** Item yang cocok, terbaik dulu (lalu favorit, lalu judul). */
    fun <T> rank(items: List<T>, target: AutofillTarget, payload: (T) -> ItemPayload): List<T> =
        items.map { it to strength(payload(it), target) }
            .filter { it.second != MatchStrength.None && (payload(it.first).password.isNotEmpty() || payload(it.first).username.isNotEmpty()) }
            .sortedWith(compareByDescending<Pair<T, MatchStrength>> { it.second.score }.thenByDescending { payload(it.first).favorite }.thenBy { payload(it.first).title.lowercase() })
            .map { it.first }

    /** Isi kolom URL item baru yang disimpan dari formulir. */
    fun urlFor(target: AutofillTarget): String {
        val web = target.trustedWebDomain?.let(::host)
        if (web != null) {
            val scheme = target.scheme?.takeIf { it == "http" || it == "https" } ?: "https"
            return "$scheme://$web"
        }
        return "$ANDROID_SCHEME${target.packageName}"
    }
}

/** Satu kolom formulir, diringkas dari ViewNode Android (supaya bisa diuji tanpa Android). */
data class FieldInfo(
    val autofillHints: List<String> = emptyList(),
    val inputType: Int = 0,
    val htmlTag: String? = null,
    val htmlType: String? = null,
    val htmlName: String? = null,
    val idEntry: String? = null,
    val hint: String? = null,
    val isEditable: Boolean = true,
    val isVisible: Boolean = true,
)

/** Hasil pengenalan formulir: indeks kolom dalam daftar [FieldInfo]. */
data class FormFields(val username: Int?, val passwords: List<Int>) {
    val isEmpty: Boolean get() = username == null && passwords.isEmpty()
}

object FieldClassifier {
    // android.text.InputType
    private const val CLASS_MASK = 0x0000000f
    private const val VARIATION_MASK = 0x00000ff0
    private const val CLASS_TEXT = 1
    private const val VAR_EMAIL = 0x20
    private const val VAR_PASSWORD = 0x80
    private const val VAR_VISIBLE_PASSWORD = 0x90
    private const val VAR_WEB_EMAIL = 0xd0
    private const val VAR_WEB_PASSWORD = 0xe0

    private val PASSWORD_WORDS = Regex("pass(word|wd)?|pwd|sandi|kata ?sandi|kata_sandi|katasandi", RegexOption.IGNORE_CASE)
    private val USER_WORDS = Regex("user|login|email|e-mail|surel|pengguna|account|akun|nik|phone|ponsel|telepon", RegexOption.IGNORE_CASE)
    private val IGNORE_WORDS = Regex("search|cari|otp|captcha|promo|kupon|voucher|cvv|cvc|card|kartu", RegexOption.IGNORE_CASE)

    private fun words(f: FieldInfo) = listOfNotNull(f.htmlName, f.idEntry, f.hint).joinToString(" ")

    fun isPassword(f: FieldInfo): Boolean {
        val hints = f.autofillHints.map { it.lowercase() }
        if (hints.any { it.contains("password") }) return true
        if (f.htmlType.equals("password", ignoreCase = true)) return true
        val cls = f.inputType and CLASS_MASK
        val variation = f.inputType and VARIATION_MASK
        if (cls == CLASS_TEXT && variation in setOf(VAR_PASSWORD, VAR_VISIBLE_PASSWORD, VAR_WEB_PASSWORD)) return true
        val w = words(f)
        return PASSWORD_WORDS.containsMatchIn(w) && !IGNORE_WORDS.containsMatchIn(w)
    }

    fun isUsername(f: FieldInfo): Boolean {
        val hints = f.autofillHints.map { it.lowercase() }
        if (hints.any { it == "username" || it == "emailaddress" || it == "email" || it.contains("username") }) return true
        if (f.htmlType.equals("email", ignoreCase = true)) return true
        val variation = f.inputType and VARIATION_MASK
        if ((f.inputType and CLASS_MASK) == CLASS_TEXT && (variation == VAR_EMAIL || variation == VAR_WEB_EMAIL)) return true
        val w = words(f)
        return USER_WORDS.containsMatchIn(w) && !IGNORE_WORDS.containsMatchIn(w)
    }

    private fun isTextCandidate(f: FieldInfo): Boolean {
        if (!f.isEditable || !f.isVisible) return false
        if (IGNORE_WORDS.containsMatchIn(words(f))) return false
        if (f.htmlTag != null && !f.htmlTag.equals("input", ignoreCase = true)) return false
        val type = f.htmlType?.lowercase()
        if (type != null && type !in setOf("text", "email", "tel", "")) return false
        return f.htmlTag != null || (f.inputType and CLASS_MASK) == CLASS_TEXT
    }

    /**
     * Kenali kolom sandi dan nama pengguna. Bila nama pengguna tidak bertanda jelas, dipakai kolom
     * teks terakhir SEBELUM kolom sandi pertama (pola formulir masuk yang paling umum).
     */
    fun classify(fields: List<FieldInfo>): FormFields {
        val usable = fields.withIndex().filter { it.value.isEditable && it.value.isVisible }
        val passwords = usable.filter { isPassword(it.value) }.map { it.index }
        val explicitUser = usable.firstOrNull { it.index !in passwords && isUsername(it.value) }?.index
        val user = explicitUser ?: passwords.firstOrNull()?.let { firstPw ->
            usable.lastOrNull { it.index < firstPw && it.index !in passwords && isTextCandidate(it.value) }?.index
        }
        return FormFields(user, passwords)
    }
}
