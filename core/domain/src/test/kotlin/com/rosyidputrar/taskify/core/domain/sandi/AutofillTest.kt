package com.rosyidputrar.taskify.core.domain.sandi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // Heuristik nama paket DIHAPUS: item web tidak lagi cocok dengan aplikasi apa pun,
        // betapa pun mirip namanya. Hanya ikatan androidapp:// eksplisit yang Exact.
        val tokped = AutofillTarget("com.tokopedia.tkpd")
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://www.tokopedia.com"), tokped))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://shopee.co.id"), AutofillTarget("com.shopee.id")))
        // Browser tanpa domain web: tidak menebak dari nama paket.
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://android.com"), AutofillTarget("com.android.chrome")))
    }

    @Test fun sideloadedPackageGetsNoDirectDataset() {
        val google = item("https://accounts.google.com")
        // Paket sideload yang meniru nama domain korban: tidak boleh dapat dataset langsung.
        assertEquals(MatchStrength.None, AutofillMatch.strength(google, AutofillTarget("com.google.evil")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(google, AutofillTarget("com.evil.google")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://www.tokopedia.com"), AutofillTarget("com.tokopedia.evil")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://www.tokopedia.com"), AutofillTarget("com.tokopedia.tkpd")))
        // Jalur yang sah tetap jalan: ikatan androidapp:// eksplisit.
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("androidapp://com.bca"), AutofillTarget("com.bca")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("androidapp://com.bca"), AutofillTarget("com.bca.evil")))
        // rank() juga tidak lagi mengembalikan item web untuk paket sideload.
        assertEquals(emptyList(), AutofillMatch.rank(listOf(google), AutofillTarget("com.google.evil")) { it }.map { it.title })
    }

    @Test fun unknownBrowserWithoutWebDomainGetsNoHeuristicMatch() {
        // Browser tak dikenal tanpa webDomain: dulu jatuh ke heuristik paket.
        val yandex = AutofillTarget("com.yandex.browser")
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://yandex.com"), yandex))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://mail.yandex.com"), yandex))
        // Ikatan androidapp:// eksplisit tetap sah.
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("androidapp://com.yandex.browser"), yandex))
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
        assertEquals("http://example.com", AutofillMatch.urlFor(AutofillTarget("com.android.chrome", "www.example.com", "http")))
    }

    @Test fun publicSuffixSubdomainsDoNotMatch() {
        // Sufiks publik (PSL): subdomain pengguna yang berbeda adalah domain yang berbeda.
        val aliceGithub = AutofillTarget("com.android.chrome", "evil.github.io")
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://alice.github.io"), aliceGithub))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://myapp.vercel.app"), AutofillTarget("com.android.chrome", "evil.vercel.app")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.netlify.app"), AutofillTarget("com.android.chrome", "b.netlify.app")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.web.app"), AutofillTarget("com.android.chrome", "b.web.app")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.firebaseapp.com"), AutofillTarget("com.android.chrome", "b.firebaseapp.com")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.appspot.com"), AutofillTarget("com.android.chrome", "b.appspot.com")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.azurewebsites.net"), AutofillTarget("com.android.chrome", "b.azurewebsites.net")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.herokuapp.com"), AutofillTarget("com.android.chrome", "b.herokuapp.com")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.pages.dev"), AutofillTarget("com.android.chrome", "b.pages.dev")))
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://a.workers.dev"), AutofillTarget("com.android.chrome", "b.workers.dev")))
        // Subdomain dari registrable yang SAMA tetap Domain; host sama tetap Exact.
        // ("www." dibuang host(), jadi dipakai subdomain lain untuk kasus Domain.)
        assertEquals(MatchStrength.Domain, AutofillMatch.strength(item("https://alice.github.io"), AutofillTarget("com.android.chrome", "app.alice.github.io")))
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("https://alice.github.io"), AutofillTarget("com.android.chrome", "alice.github.io")))
        // registrable() untuk tiap sufiks di atas: satu label di atas sufiks publik.
        assertEquals("alice.github.io", AutofillMatch.registrable("a.alice.github.io"))
        assertEquals("myapp.vercel.app", AutofillMatch.registrable("www.myapp.vercel.app"))
        assertEquals("a.netlify.app", AutofillMatch.registrable("a.netlify.app"))
        assertEquals("a.web.app", AutofillMatch.registrable("a.web.app"))
        assertEquals("a.firebaseapp.com", AutofillMatch.registrable("a.firebaseapp.com"))
        assertEquals("a.appspot.com", AutofillMatch.registrable("a.appspot.com"))
        assertEquals("a.azurewebsites.net", AutofillMatch.registrable("a.azurewebsites.net"))
        assertEquals("a.herokuapp.com", AutofillMatch.registrable("a.herokuapp.com"))
        assertEquals("a.pages.dev", AutofillMatch.registrable("a.pages.dev"))
        assertEquals("a.workers.dev", AutofillMatch.registrable("a.workers.dev"))
    }

    @Test fun ipLiteralsAreNotDomainMatches() {
        assertEquals("192.168.1.1", AutofillMatch.registrable("192.168.1.1"))
        assertEquals("10.0.1.1", AutofillMatch.registrable("10.0.1.1"))
        val lan = AutofillTarget("com.android.chrome", "10.0.1.1")
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("http://192.168.1.1"), lan))
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("http://10.0.1.1"), lan))
    }

    @Test fun httpsItemNotOfferedToHttpTarget() {
        val httpTarget = AutofillTarget("com.android.chrome", "accounts.google.com", "http")
        assertEquals(MatchStrength.None, AutofillMatch.strength(item("https://accounts.google.com"), httpTarget))
        val httpsTarget = AutofillTarget("com.android.chrome", "accounts.google.com", "https")
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("https://accounts.google.com"), httpsTarget))
        // Skema tidak diketahui (framework tidak memberinya): perilaku lama dipertahankan.
        val unknownTarget = AutofillTarget("com.android.chrome", "accounts.google.com")
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("https://accounts.google.com"), unknownTarget))
        // Item http ke target http tetap Exact (bukan downgrade).
        assertEquals(MatchStrength.Exact, AutofillMatch.strength(item("http://accounts.google.com"), httpTarget))
    }

    @Test fun packageOfRejectsAtSign() {
        assertNull(AutofillMatch.packageOf("androidapp://com.bca@com.evil"))
        assertNull(AutofillMatch.packageOf("androidapp://com.bca@"))
        assertEquals("com.bca", AutofillMatch.packageOf("androidapp://com.bca"))
        assertEquals("com.bca.evil", AutofillMatch.packageOf("androidapp://com.bca.evil"))
        assertNull(AutofillMatch.packageOf("androidapp://com..bca"))
        assertNull(AutofillMatch.packageOf("androidapp://9com.bca"))
    }

    @Test fun hostRejectsMalformedLabels() {
        assertNull(AutofillMatch.host("https://google..com"))
        assertNull(AutofillMatch.host("https://-evil-.com"))
        assertNull(AutofillMatch.host("https://exa mple.com"))
        assertNull(AutofillMatch.host("https://" + "a".repeat(64) + ".com"))
        assertEquals("::1", AutofillMatch.host("https://[::1]:8080/x"))
        assertEquals("login.example.com", AutofillMatch.host("https://Login.Example.com:443/a?b"))
    }

    @Test fun displayHostShowsPunycodeForHomograph() {
        assertEquals("google.com", AutofillMatch.displayHost("google.com"))
        // "gооgle.com" dengan huruf Cyrillic о (U+043E): tampil sebagai punycode, bukan google.com.
        val homograph = "g\u043E\u043Egle.com"
        val shown = AutofillMatch.displayHost(homograph)
        assertTrue(shown.startsWith("xn--"), "homograph harus tampil sebagai punycode, dapat: $shown")
        assertTrue(!shown.contains("\u043E"), "punycode tidak boleh memuat huruf Cyrillic")
    }

    @Test fun itemPayloadToStringRedactsPassword() {
        val s = ItemPayload(title = "Google", url = "https://google.com", username = "rosyid", password = "rahasia-123").toString()
        assertTrue(!s.contains("rahasia-123"), "toString tidak boleh memuat sandi: $s")
        assertTrue(s.contains("•••"), "toString harus menandai redaksi: $s")
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
