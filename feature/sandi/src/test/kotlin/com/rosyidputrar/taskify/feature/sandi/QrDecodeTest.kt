package com.rosyidputrar.taskify.feature.sandi

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rosyidputrar.taskify.core.domain.sandi.AuthAccount
import com.rosyidputrar.taskify.core.domain.sandi.AuthenticatorImport
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class QrDecodeTest {
    private fun pixels(file: File): Triple<IntArray, Int, Int> {
        val img = ImageIO.read(file)
        return Triple(img.getRGB(0, 0, img.width, img.height, null, 0, img.width), img.width, img.height)
    }

    /** Gambar QR ekspor Taskify (golden, dialog gelap) → dibaca lagi → akun & kunci utuh. */
    @Test fun readsTaskifyExportGoldenScreenshot() {
        val (px, w, h) = pixels(File("../../app/screenshots/sandi_export_qr_dark.png"))
        val text = QrDecode.decode(px, w, h).single()
        assertEquals(
            listOf(
                AuthAccount("Cloudflare", "ops@example.com", TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")),
                AuthAccount("GitHub", "rosyid@example.com", TotpConfig("JBSWY3DPEHPK3PXP")),
            ),
            AuthenticatorImport.parse(text).single().accounts,
        )
    }

    private fun render(text: String, invert: Boolean, scale: Int = 4): Triple<IntArray, Int, Int> {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
        val w = m.width * scale
        val px = IntArray(w * w) { i ->
            val on = m.get((i % w) / scale, (i / w) / scale)
            if (on != invert) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Triple(px, w, w)
    }

    @Test fun readsNormalAndInvertedCodes() {
        val text = "otpauth://totp/GitHub:rosyid?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        for (invert in listOf(false, true)) {
            val (px, w, h) = render(text, invert)
            assertEquals(listOf(text), QrDecode.decode(px, w, h), "invert=$invert")
        }
    }

    @Test fun imageWithoutQrGivesNothing() {
        val px = IntArray(200 * 200) { if ((it / 7) % 2 == 0) 0xFF808080.toInt() else 0xFFFFFFFF.toInt() }
        assertEquals(emptyList(), QrDecode.decode(px, 200, 200))
    }
}
