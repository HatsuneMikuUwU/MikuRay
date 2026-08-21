package com.miku.ray.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QRCodeDecoder {
    val HINTS: MutableMap<DecodeHintType, Any?> = EnumMap(DecodeHintType::class.java)

    fun createQRCode(text: String, size: Int = 800): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8)
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size) { i ->
                if (bitMatrix.get(i % size, i / size)) 0xff000000.toInt() else 0xffffffff.toInt()
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, size, 0, 0, size, size)
            }
        }.getOrNull()
    }

    fun syncDecodeQRCode(picturePath: String): String? {
        return syncDecodeQRCode(getDecodeAbleBitmap(picturePath))
    }

    fun syncDecodeQRCode(bitmap: Bitmap?): String? {
        return bitmap?.let {
            runCatching {
                val pixels = IntArray(it.width * it.height).also { array ->
                    it.getPixels(array, 0, it.width, 0, 0, it.width, it.height)
                }
                val source = RGBLuminanceSource(it.width, it.height, pixels)
                val qrReader = QRCodeReader()

                try {
                    qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), HINTS).text
                } catch (e: NotFoundException) {
                    qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())), HINTS).text
                }
            }.getOrNull()
        }
    }

    private fun getDecodeAbleBitmap(picturePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(picturePath, options)
            var sampleSize = options.outHeight / 400
            if (sampleSize <= 0) {
                sampleSize = 1
            }
            options.inSampleSize = sampleSize
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(picturePath, options)
        } catch (e: Exception) {
            null
        }
    }

    init {
        HINTS[DecodeHintType.TRY_HARDER] = true
        HINTS[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        HINTS[DecodeHintType.CHARACTER_SET] = Charsets.UTF_8.name()
    }
}
