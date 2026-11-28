package com.example.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetectedBarcode(
    val rawValue: String,
    val formatLabel: String,
    val isUrl: Boolean
)

/**
 * Zero-token, 100% on-device QR & Barcode detector for the Live Lens preview stream.
 * Decodes QR codes, URLs, UPC/EAN, Code128, Code39, PDF417, DataMatrix, and Aztec barcodes
 * directly from camera preview bitmaps without calling any cloud vision/LLM endpoint.
 */
object LiveLensBarcodeDetector {

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.ITF
        )
    )

    suspend fun detect(bitmap: Bitmap): DetectedBarcode? = withContext(Dispatchers.Default) {
        val scaled = downsampleIfNeeded(bitmap, maxSide = 640)
        try {
            val width = scaled.width
            val height = scaled.height
            if (width < 32 || height < 32) return@withContext null

            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(decodeHints) }

            val result = runCatching { reader.decodeWithState(binaryBitmap) }.getOrNull()
                ?: runCatching {
                    // Secondary pass with GlobalHistogramBinarizer for high-glare receipts/screens
                    reader.decodeWithState(
                        BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
                    )
                }.getOrNull()
                ?: return@withContext null

            val text = result.text?.trim().orEmpty()
            if (text.isEmpty()) return@withContext null

            val isUrl = text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ||
                Regex("^(www\\.)[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}.*").matches(text)

            DetectedBarcode(
                rawValue = text,
                formatLabel = result.barcodeFormat.name.replace('_', ' '),
                isUrl = isUrl
            )
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    private fun downsampleIfNeeded(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(32)
        val h = (src.height * ratio).toInt().coerceAtLeast(32)
        return runCatching { Bitmap.createScaledBitmap(src, w, h, true) }.getOrDefault(src)
    }
}
