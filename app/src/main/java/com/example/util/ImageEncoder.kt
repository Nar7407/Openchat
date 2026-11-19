package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class EncodedImage(
    val localFileUri: String,
    val base64: String,
    val mimeType: String = "image/jpeg"
)

object ImageEncoder {

    suspend fun persistAndEncode(context: Context, sourceUri: Uri): EncodedImage =
        withContext(Dispatchers.IO) {
            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read selected image.")

            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: throw IllegalStateException("Unsupported image format.")

            val scaled = scaleDownIfNeeded(decoded, maxDimension = 1280)
            val jpegStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, jpegStream)
            val jpegBytes = jpegStream.toByteArray()

            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val destFile = File(dir, "att_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { it.write(jpegBytes) }

            EncodedImage(
                localFileUri = Uri.fromFile(destFile).toString(),
                base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
        }

    suspend fun encodeFromStoredUri(context: Context, uriString: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bytes = if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).readBytes()
                } else {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@runCatching null
                Base64.encodeToString(bytes, Base64.NO_WRAP) to "image/jpeg"
            }.getOrNull()
        }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val ratio = if (w >= h) maxDimension.toFloat() / w else maxDimension.toFloat() / h
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
