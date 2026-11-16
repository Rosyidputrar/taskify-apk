package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.TotpAlgorithm
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QR "pindahkan ke authenticator lain" harus terbaca kembali persis sama. */
class TotpUriTest {
    @Test fun standardKeyUri() {
        val uri = Totp.otpauthUri(TotpConfig("JBSWY3DPEHPK3PXP"), "GitHub", "rosyid@example.com")
        assertEquals("otpauth://totp/GitHub:rosyid%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30", uri)
    }

    @Test fun roundTripsThroughParser() {
        val cfg = TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", digits = 8, period = 60, algorithm = TotpAlgorithm.SHA256)
        val parsed = Totp.parseInput(Totp.otpauthUri(cfg, "Bank Jago: Mobile", "rosyid putra"))!!
        assertEquals(cfg, parsed.config)
        assertEquals("Bank Jago Mobile", parsed.name) // ":" di penerbit merusak label → dibuang
        assertEquals("rosyid putra", parsed.username)
        // Kode yang dihasilkan dari QR sama dengan kode di Taskify.
        assertEquals(Totp.window(cfg, 1_763_175_612_000L)!!.code, Totp.window(parsed.config, 1_763_175_612_000L)!!.code)
    }

    @Test fun withoutIssuerOrAccount() {
        val noIssuer = Totp.parseInput(Totp.otpauthUri(TotpConfig("JBSWY3DPEHPK3PXP"), "", "akun"))!!
        assertEquals("akun", noIssuer.username)
        val nothing = Totp.otpauthUri(TotpConfig("JBSWY3DPEHPK3PXP"), " ", "")
        assertTrue(nothing.startsWith("otpauth://totp/Taskify?secret="))
    }
}
