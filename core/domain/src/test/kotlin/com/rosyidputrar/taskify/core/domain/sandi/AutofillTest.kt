package com.rosyidputrar.taskify.core.domain.sandi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutofillTest {
    private fun item(url: String, title: String = url, user: String = "rosyid", fav: Boolean = false) =
        ItemPayload(title = title, url = url, username = user, password = "rahasia", favorite = fav)

    @Test fun hostNormalisation() {
        assertEquals("login.example.com", AutofillMatch.host("https://Login.Example.com:443/a?b"))
        assertEquals("example.com", AutofillMatch.host("www.example.com"))
        assertEquals("example.com", AutofillMatch.host("http://user@example.com/"))
        assertNull(AutofillMatch.host("androidapp://com.bca"))
        assertNull(AutofillMatch.host("Catatan bank"))
        assertNull(AutofillMatch.host(""))
    }

    @Test fun registrableDomainKnowsIndonesianSuffixes() {
        assertEquals("tokopedia.com", AutofillMatch.registrable("accounts.tokopedia.com"))
        assertEquals("klikbca.co.id", AutofillMatch.registrable("ibank.klikbca.co.id"))
        assertEquals("ui.ac.id", AutofillMatch.registrable("sso.ui.ac.id"))
        assertEquals("example.com", AutofillMatch.registrable("example.com"))
    }

    @Test fun webMatchingIsStrictAboutDomain() {
        val target = AutofillTarget("com.android.chrome", "accounts.google.com")
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("https://accounts.google.com"), target))
        assertEquals(MatchStrength.Domain, AutofillMatch.strength(item("google.com"), target))
        // Anti-phishing: domain mirip tidak dianggap cocok.
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://google.com.evil.io"), target))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://accounts-google.com"), target))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("androidapp://com.android.chrome"), target))
    }

    @Test fun appMatchingByPackage() {
        val bca = AutofillTarget("com.bca")
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("androidapp://com.bca"), bca))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("androidapp://com.bca.evil"), bca))
        val tokped = AutofillTarget("com.tokopedia.tkpd")
        assertEquals(MatchStrength.Domain, AutofillMatch.strength(item("https://www.tokopedia.com"), tokped))
        assertEquals(MatchStrength.Related, AutofillMatch.strength(item("https://shopee.co.id"), AutofillTarget("com.shopee.id")))
        // Browser tanpa domain web: tidak menebak dari nama paket.
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://android.com"), AutofillTarget("com.android.chrome")))
    }

    @Test fun rankPutsExactFirstAndDropsEmpty() {
        val target = AutofillTarget("org.mozilla.firefox", "mail.example.com")
        val list = listOf(
            item("example.com", "B umum"),
            item("https://mail.example.com", "Z tepat"),
            item("example.com", "A favorit", fav = true),
            item("other.com", "Lain"),
            ItemPayload(title = "Kosong", url = "example.com"),
        )
        assertEquals(listOf("Z tepat", "A favorit", "B umum"), AutofillMatch.rank(list, target) { it }.map { it.title })
    }

    @Test fun urlForNewItems() {
        assertEquals("https://example.com", AutofillMatch.urlFor(AutofillTarget("com.android.chrome", "www.example.com")))
        assertEquals("androidapp://com.bca", AutofillMatch.urlFor(AutofillTarget("com.bca")))
    }

    private val text = 0x1
    private val textPassword = 0x81
    private val textEmail = 0x21

    @Test fun classifiesNativeLoginForm() {
        val form = listOf(
            FieldInfo(inputType = text, idEntry = "search_box"),
            FieldInfo(inputType = text, idEntry = "et_user_id"),
            FieldInfo(inputType = textPassword, idEntry = "et_pin"),
        )
        assertEquals(FormFields(1, listOf(2)), FieldClassifier.classify(form))
    }

    @Test fun usesHintsAndEmailVariation() {
        assertEquals(FormFields(0, listOf(1)), FieldClassifier.classify(listOf(FieldInfo(inputType = textEmail), FieldInfo(autofillHints = listOf("password")))))
        assertEquals(FormFields(1, listOf(0)), FieldClassifier.classify(listOf(FieldInfo(autofillHints = listOf("password")), FieldInfo(autofillHints = listOf("username")))))
    }

    @Test fun webFormFallsBackToFieldBeforePassword() {
        val form = listOf(
            FieldInfo(htmlTag = "input", htmlType = "text", htmlName = "q", hint = "Cari"),
            FieldInfo(htmlTag = "input", htmlType = "text", htmlName = "field_1"),
            FieldInfo(htmlTag = "input", htmlType = "password", htmlName = "field_2"),
            FieldInfo(htmlTag = "input", htmlType = "password", htmlName = "confirm"),
        )
        assertEquals(FormFields(1, listOf(2, 3)), FieldClassifier.classify(form))
    }

    @Test fun ignoresOtpAndHiddenFields() {
        val form = listOf(
            FieldInfo(inputType = text, idEntry = "otp_code"),
            FieldInfo(inputType = textPassword, isVisible = false),
        )
        assertEquals(true, FieldClassifier.classify(form).isEmpty)
    }

    @Test fun indonesianLabels() {
        val form = listOf(FieldInfo(inputType = text, hint = "Nama pengguna"), FieldInfo(inputType = text, hint = "Kata sandi"))
        assertEquals(FormFields(0, listOf(1)), FieldClassifier.classify(form))
    }
}
