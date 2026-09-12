package com.ringly.app.sync

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object ContactPhotoEncoder {

    const val MAX_DIMENSION = 512
    const val JPEG_QUALITY = 80

    fun encode(bitmap: Bitmap): String {
        val scaled = scaleToMaxDimension(bitmap, MAX_DIMENSION)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, max: Int): Bitmap {
        if (bitmap.width <= max && bitmap.height <= max) return bitmap
        val ratio = max.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * ratio).toInt()),
            maxOf(1, (bitmap.height * ratio).toInt()),
            true
        )
    }
}