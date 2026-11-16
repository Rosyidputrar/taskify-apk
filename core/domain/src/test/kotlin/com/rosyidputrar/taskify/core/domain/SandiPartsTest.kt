package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigParser
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.MasterPassword
import com.rosyidputrar.taskify.core.domain.sandi.PassphraseOptions
import com.rosyidputrar.taskify.core.domain.sandi.PasswordOptions
import com.rosyidputrar.taskify.core.domain.sandi.RandomSource
import com.rosyidputrar.taskify.core.domain.sandi.RotationBlocker
import com.rosyidputrar.taskify.core.domain.sandi.RotationProbe
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.TotpAlgorithm
import com.rosyidputrar.taskify.core.domain.sandi.TrustMeta
import com.rosyidputrar.taskify.core.domain.sandi.TrustState
import com.rosyidputrar.taskify.core.domain.sandi.UNREACHABLE
import com.rosyidputrar.taskify.core.domain.sandi.displayHost
import com.rosyidputrar.taskify.core.domain.sandi.randomInt
import com.rosyidputrar.taskify.core.domain.sandi.rotationBlocker
import com.rosyidputrar.taskify.core.domain.sandi.safeUrl
import com.rosyidputrar.taskify.core.domain.sandi.shouldLockSandi
import com.rosyidputrar.taskify.core.domain.sandi.trustState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandiTotpTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val seed = mapOf(
        TotpAlgorithm.SHA1 to hex("3132333435363738393031323334353637383930"),
        TotpAlgorithm.SHA256 to hex("3132333435363738393031323334353637383930313233343536373839303132"),
        TotpAlgorithm.SHA512 to hex("31323334353637383930313233343536373839303132333435363738393031323334353637383930313233343536373839303132333435363738393031323334"),
    )

    @Test fun rfc6238Vectors() {
        listOf(
            Triple(59L, TotpAlgorithm.SHA1, "94287082"), Triple(59L, TotpAlgorithm.SHA256, "46119246"), Triple(59L, TotpAlgorithm.SHA512, "90693936"),
            Triple(1111111109L, TotpAlgorithm.SHA1, "07081804"), Triple(1111111109L, TotpAlgorithm.SHA256, "68084774"), Triple(1111111109L, TotpAlgorithm.SHA512, "25091201"),
            Triple(1111111111L, TotpAlgorithm.SHA1, "14050471"), Triple(1111111111L, TotpAlgorithm.SHA256, "67062674"), Triple(1111111111L, TotpAlgorithm.SHA512, "99943326"),
        ).forEach { (sec, alg, code) -> assertEquals(code, Totp.hotp(seed.getValue(alg), Totp.counterAt(sec * 1000, 30), 8, alg), "$sec $alg") }
        assertEquals("287082", Totp.hotp(seed.getValue(TotpAlgorithm.SHA1), Totp.counterAt(59_000, 30), 6, TotpAlgorithm.SHA1))
        val w = Totp.window(com.rosyidputrar.taskify.core.domain.sandi.TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 8), 59_000)!!
        assertEquals("94287082", w.code)
        assertEquals(1_000, w.remainingMs)
    }

    @Test fun base32AndParsing() {
        assertEquals("foobar", String(Totp.decodeBase32("MZXW6YTBOI======")!!))
        assertEquals("MZXW6YTBOI", Totp.encodeBase32("foobar".toByteArray()))
        assertNull(Totp.decodeBase32("ABC"))
        assertNull(Totp.decodeBase32("01"))
        assertEquals("JBSW Y3DP EHPK 3PXP", Totp.formatSecret("jbswy3dpehpk3pxp"))
        val input = Totp.parseInput("otpauth://totp/GitHub:rosyid%40mail.id?secret=jbswy3dpehpk3pxp&issuer=GitHub&digits=8&period=60&algorithm=sha256")!!
        assertEquals("GitHub", input.name)
        assertEquals("rosyid@mail.id", input.username)
        assertEquals(8, input.config.digits)
        assertEquals(60, input.config.period)
        assertEquals(TotpAlgorithm.SHA256, input.config.algorithm)
        assertEquals("JBSWY3DPEHPK3PXP", Totp.parseInput("JBSW Y3DP EHPK 3PXP")!!.config.secret)
        assertNull(Totp.parseInput("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
        assertNotNull(Totp.problem("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
        assertEquals("Kunci rahasia terlalu pendek. Periksa lagi.", Totp.problem("JBSWY3DP"))
        assertNull(Totp.problem("JBSWY3DPEHPK3PXP"))
        assertEquals(1_500L, Totp.offsetFromSample(10_000, 8_000, 9_000))
        assertNull(Totp.offsetFromSample(0, 9_000, 8_000))
    }
}

class SandiGeneratorTest {
    @Test fun generatorRespectsOptions() {
        repeat(50) {
            val pw = Generator.password(PasswordOptions(length = 12, symbols = false, avoidAmbiguous = true))
            assertEquals(12, pw.length)
            assertTrue(pw.any { it.isLowerCase() } && pw.any { it.isUpperCase() } && pw.any { it.isDigit() })
            assertTrue(pw.none { it in Generator.AMBIGUOUS || it in Generator.SYMBOLS })
        }
        assertEquals(Generator.LENGTH_MIN, Generator.password(PasswordOptions(length = 2)).length)
        val phrase = Generator.passphrase(PassphraseOptions(words = 5, separator = "-", capitalize = true, includeNumber = true))
        assertEquals(5, phrase.split("-").size)
        assertTrue(phrase.any { it.isDigit() })
        assertEquals(7776, Generator.wordlist.size)
        assertEquals(128, Generator.passwordEntropy(PasswordOptions(length = 20)).toInt())
        assertEquals(64, Generator.passphraseEntropy(PassphraseOptions(words = 5)).toInt())
    }

    @Test fun randomIntIsUnbiasedRejectionSampling() {
        // Sumber deterministik: 0xFFFFFFFF ditolak untuk max=3 (ekor), lalu 0x00000004 → 4 % 3 = 1.
        var call = 0
        val rng = RandomSource { b -> val v = if (call++ == 0) 0xFFFFFFFF.toInt() else 4; b[0] = (v ushr 24).toByte(); b[1] = (v ushr 16).toByte(); b[2] = (v ushr 8).toByte(); b[3] = v.toByte() }
        assertEquals(1, randomInt(3, rng))
        assertEquals(2, call)
    }

    @Test fun quickEstimate() {
        assertEquals("Lemah", Generator.estimate("password").label.label)
        assertTrue(Generator.estimate("kuda biru minum kopi pagi").bits >= 55)
        assertTrue(Generator.estimate("budi2024").issues.any { it.contains("Kata diikuti angka") })
    }

    @Test fun masterPasswordGate() {
        assertEquals("Minimal 12 karakter.", MasterPassword.problem("pendek"))
        assertNotNull(MasterPassword.problem("password1234"))
        assertNotNull(MasterPassword.problem("Welcome2024!!"))
        assertNotNull(MasterPassword.problem("kopi kuda kopi kuda kopi"))
        assertNotNull(MasterPassword.problem("sayangku12345"))  // kata Indonesia + angka
        assertNull(MasterPassword.problem("lumbung-kerikil-sabana-jingga-tempias"))
        assertNull(MasterPassword.problem("q7#Lm2!vR9@zX4pT"))
        assertTrue(MasterPassword.userInputsFromEmail("rosyid.putra@mail.id").containsAll(listOf("rosyid.putra", "rosyid", "putra")))
    }
}

class SandiPolicyTest {
    @Test fun autoLock() {
        assertTrue(shouldLockSandi(now = 10_000, lastActivity = 0, hiddenAt = 10_000, autoLockMinutes = 0))
        assertFalse(shouldLockSandi(now = 10_000_000, lastActivity = 0, hiddenAt = null, autoLockMinutes = 0))
        assertTrue(shouldLockSandi(now = 300_000, lastActivity = 0, hiddenAt = null, autoLockMinutes = 5))
        assertFalse(shouldLockSandi(now = 299_999, lastActivity = 0, hiddenAt = null, autoLockMinutes = 5))
    }

    @Test fun trustAndRotationGuards() {
        val meta = TrustMeta("wk", 1_000)
        assertEquals(TrustState.Ok, trustState(meta, "wk", 999))
        assertEquals(TrustState.Expired, trustState(meta, "wk", 1_000))
        assertEquals(TrustState.Stale, trustState(meta, "baru", 10))
        val ok = RotationProbe(connected = true, hasSynced = true, downloading = false, pendingUploads = 0, remoteWrappedKey = "wk")
        assertNull(rotationBlocker(ok, "wk"))
        assertEquals(RotationBlocker.Offline, rotationBlocker(ok.copy(connected = false), "wk"))
        assertEquals(RotationBlocker.PendingUploads, rotationBlocker(ok.copy(pendingUploads = 2), "wk"))
        assertEquals(RotationBlocker.Unreachable, rotationBlocker(ok.copy(remoteWrappedKey = UNREACHABLE), "wk"))
        assertEquals(RotationBlocker.KeyChanged, rotationBlocker(ok.copy(remoteWrappedKey = "lain"), "wk"))
    }

    @Test fun urls() {
        assertEquals("https://github.com/login", safeUrl("github.com/login"))
        assertNull(safeUrl("javascript:alert(1)"))
        assertNull(safeUrl("data:text/html,x"))
        assertNull(safeUrl("intranet"))
        assertEquals("github.com", displayHost("https://www.github.com/login"))
    }
}

class ConfigParseTest {
    private fun pairs(t: String) = ConfigParser.parseEnv(t).entries.associate { it.key to it.value }

    @Test fun env() {
        val p = ConfigParser.parseEnv(listOf("# komentar", "", "API_URL=https://api.example.com", "DEBUG=1", "").joinToString("\n"))
        assertEquals(listOf(Triple("API_URL", "https://api.example.com", 3), Triple("DEBUG", "1", 4)), p.entries.map { Triple(it.key, it.value, it.line) })
        assertTrue(p.problems.isEmpty())
        assertEquals(mapOf("TOKEN" to "abc", "EMPTY" to "", "SPACED" to "x"), pairs("export TOKEN=abc\nEMPTY=\nSPACED = x"))
        assertEquals(mapOf("A" to "satu dua", "B" to "p#ss", "C" to "nilai", "D" to "a#b"), pairs("A=\"satu dua\"\nB='p#ss'\nC=nilai # komentar\nD=a#b"))
        val multi = ConfigParser.parseEnv("KEY=\"-----BEGIN-----\nbaris2\n-----END-----\"\nAFTER=1")
        assertEquals(listOf("KEY", "AFTER"), multi.entries.map { it.key })
        assertEquals("-----BEGIN-----\nbaris2\n-----END-----", multi.entries[0].value)
        val open = ConfigParser.parseEnv("KEY=\"tidak ditutup\nLAIN=1")
        assertTrue(open.blocking)
        assertEquals(1, open.problems[0].line)
        val dup = ConfigParser.parseEnv("A=satu\nB=x\nA=dua")
        assertEquals(mapOf("A" to "dua", "B" to "x"), pairs("A=satu\nB=x\nA=dua"))
        assertEquals(listOf("A", "B"), dup.entries.map { it.key })
        assertTrue(dup.problems[0].message.contains("baris 1"))
        assertEquals(3, dup.problems[0].line)
        assertEquals(2, ConfigParser.parseEnv("A=1\nbaris ngawur\nB=2").problems.single().line)
        assertEquals(mapOf("A" to "1", "B" to "2"), pairs("A=1\r\nB=2"))
    }

    @Test fun jsonAndDiff() {
        assertEquals(
            mapOf("db.pool.max" to "10", "hosts.0" to "a", "hosts.1" to "b", "on" to "true"),
            ConfigParser.parseJson("{\"db\":{\"pool\":{\"max\":10}},\"hosts\":[\"a\",\"b\"],\"on\":true}").entries.associate { it.key to it.value },
        )
        assertEquals(mapOf("a" to "null", "b" to "{}", "c" to "[]"), ConfigParser.parseJson("{\"a\":null,\"b\":{},\"c\":[]}").entries.associate { it.key to it.value })
        assertTrue(ConfigParser.parseJson("{\"a\": }").blocking)
        assertFalse(ConfigParser.parseJson("[1,2]").blocking)
        assertEquals(ConfigFormat.Json, ConfigParser.guessFormat("config.json"))
        assertEquals(ConfigFormat.Env, ConfigParser.guessFormat(".env.production"))
        assertEquals(ConfigFormat.Json, ConfigParser.guessFormat("x", "{\"a\":1}"))
        val d = ConfigParser.diff(ConfigParser.parseEnv("A=1\nB=2\nC=3").entries, ConfigParser.parseEnv("# baru\nA=1\nB=9\nD=4").entries)
        assertEquals("+1 · 1 berubah · −1", ConfigParser.summarize(d))
        assertNull(ConfigParser.summarize(ConfigParser.diff(ConfigParser.parseEnv("A=1\nB=2").entries, ConfigParser.parseEnv("B=2\n# x\nA=1").entries)))
        assertEquals(null, ConfigParser.sizeProblem("é".repeat(ConfigParser.MAX_BYTES / 2)))
        assertNotNull(ConfigParser.sizeProblem("é".repeat(ConfigParser.MAX_BYTES / 2) + "x"))
        assertEquals("production-eu", ConfigParser.normalizeEnvironment("  Production EU "))
    }
}
