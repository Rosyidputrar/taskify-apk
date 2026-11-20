package com.rosyidputrar.taskify.feature.sandi

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.multi.qrcode.QRCodeMultiReader

/**
 * Baca semua QR di satu gambar (mis. foto layar "Transfer akun" dari HP lain). Murni JVM supaya bisa
 * diuji tanpa Android. [argb] = piksel ARGB baris per baris.
 */
object QrDecode {
    private val HINTS = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    fun decode(argb: IntArray, width: Int, height: Int): List<String> {
        val source: LuminanceSource = RGBLuminanceSource(width, height, argb)
        // Foto layar: coba dua cara binarisasi, lalu gambar terbalik (QR terang di latar gelap).
        val sources = listOf(source, source.invert())
        for (s in sources) {
            for (bitmap in listOf(BinaryBitmap(HybridBinarizer(s)), BinaryBitmap(GlobalHistogramBinarizer(s)))) {
                val many = try {
                    QRCodeMultiReader().decodeMultiple(bitmap, HINTS).mapNotNull { it.text }
                } catch (_: NotFoundException) {
                    emptyList()
                }
                if (many.isNotEmpty()) return many.distinct()
                val one = try { QRCodeReader().decode(bitmap, HINTS).text } catch (_: ReaderException) { null }
                if (one != null) return listOf(one)
            }
        }
        return emptyList()
    }
}
