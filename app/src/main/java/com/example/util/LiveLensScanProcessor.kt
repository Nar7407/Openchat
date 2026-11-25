package com.example.util

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScanEnhanceResult(
    val bitmap: Bitmap,
    val wasAutoCropped: Boolean
)

/**
 * Native document scanner pipeline for Live Lens ("Scan Enhance" mode):
 * 1) Detects bright document / paper / receipt / whiteboard boundaries against darker margins
 *    and auto-crops to the page bounding box.
 * 2) Applies adaptive grayscale contrast stretching + 3x3 unsharp sharpening so tiny textbook
 *    print, receipts, and handwritten notes are razor-sharp for vision OCR & analysis.
 */
object LiveLensScanProcessor {

    fun enhanceDocumentBitmap(source: Bitmap): ScanEnhanceResult {
        val working = downscaleIfNeeded(source, maxEdge = 1440)
        val (cropped, didCrop) = autoCropBrightDocument(working)
        val enhanced = applyAdaptiveGrayscaleAndSharpen(cropped)
        if (working !== source && working !== cropped) {
            working.recycle()
        }
        if (cropped !== source && cropped !== enhanced) {
            cropped.recycle()
        }
        return ScanEnhanceResult(bitmap = enhanced, wasAutoCropped = didCrop)
    }

    private fun downscaleIfNeeded(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = max(w, h)
        if (longest <= maxEdge || w <= 0 || h <= 0) return src
        val scale = maxEdge.toFloat() / longest.toFloat()
        val nw = (w * scale).roundToInt().coerceAtLeast(1)
        val nh = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    /**
     * Scans a fast luminance grid to find a bright paper/document bounding rectangle.
     * Only crops when a clear document region covers 18%..93% of the frame so full-frame
     * or dark scenes are never over-cropped.
     */
    private fun autoCropBrightDocument(src: Bitmap): Pair<Bitmap, Boolean> {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return src to false

        val gridW = 96
        val gridH = ((h.toFloat() / w.toFloat()) * gridW).roundToInt().coerceIn(48, 128)
        val lumaGrid = IntArray(gridW * gridH)
        val hist = IntArray(256)

        for (gy in 0 until gridH) {
            val py = ((gy + 0.5f) * h / gridH).toInt().coerceIn(0, h - 1)
            for (gx in 0 until gridW) {
                val px = ((gx + 0.5f) * w / gridW).toInt().coerceIn(0, w - 1)
                val c = src.getPixel(px, py)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
                lumaGrid[gy * gridW + gx] = luma
                hist[luma]++
            }
        }

        val total = gridW * gridH
        val p30 = percentileFromHist(hist, total, 0.30f)
        val p80 = percentileFromHist(hist, total, 0.80f)
        // Need a meaningful brightness gap between background and paper
        if (p80 - p30 < 28 || p80 < 120) {
            return src to false
        }
        val brightThreshold = ((p30 * 0.38f + p80 * 0.62f).toInt()).coerceIn(105, 220)

        val rowCounts = IntArray(gridH)
        val colCounts = IntArray(gridW)
        for (gy in 0 until gridH) {
            val rowOff = gy * gridW
            for (gx in 0 until gridW) {
                if (lumaGrid[rowOff + gx] >= brightThreshold) {
                    rowCounts[gy]++
                    colCounts[gx]++
                }
            }
        }

        val minRowBright = (gridW * 0.16f).toInt().coerceAtLeast(4)
        val minColBright = (gridH * 0.16f).toInt().coerceAtLeast(4)

        val topG = (0 until gridH).firstOrNull { rowCounts[it] >= minRowBright } ?: return src to false
        val bottomG = (gridH - 1 downTo 0).firstOrNull { rowCounts[it] >= minRowBright } ?: return src to false
        val leftG = (0 until gridW).firstOrNull { colCounts[it] >= minColBright } ?: return src to false
        val rightG = (gridW - 1 downTo 0).firstOrNull { colCounts[it] >= minColBright } ?: return src to false

        if (rightG <= leftG + 4 || bottomG <= topG + 4) return src to false

        val padX = (w * 0.02f).roundToInt()
        val padY = (h * 0.02f).roundToInt()
        val left = ((leftG * w) / gridW - padX).coerceIn(0, w - 1)
        val top = ((topG * h) / gridH - padY).coerceIn(0, h - 1)
        val right = (((rightG + 1) * w) / gridW + padX).coerceIn(left + 1, w)
        val bottom = (((bottomG + 1) * h) / gridH + padY).coerceIn(top + 1, h)

        val cropW = right - left
        val cropH = bottom - top
        val areaRatio = (cropW.toLong() * cropH.toLong()).toFloat() / (w.toLong() * h.toLong()).toFloat()

        if (areaRatio < 0.18f || areaRatio > 0.93f) {
            return src to false
        }

        val cropped = Bitmap.createBitmap(src, left, top, cropW, cropH)
        return cropped to true
    }

    /**
     * Converts to crisp adaptive grayscale with percentile contrast stretching and a lightweight
     * 3x3 unsharp sharpening pass tailored for small printed text, receipts, and handwritten notes.
     */
    private fun applyAdaptiveGrayscaleAndSharpen(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val count = w * h
        val pixels = IntArray(count)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = IntArray(count)
        val hist = IntArray(256)
        for (i in 0 until count) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
            luma[i] = y
            hist[y]++
        }

        val blackPoint = percentileFromHist(hist, count, 0.04f).coerceAtMost(130)
        val whitePoint = percentileFromHist(hist, count, 0.92f).coerceAtLeast(blackPoint + 45)
        val span = (whitePoint - blackPoint).toFloat().coerceAtLeast(1f)

        // Contrast-stretched LUT with gentle S-curve to darken ink and whiten paper background
        val lut = IntArray(256) { v ->
            val norm = ((v - blackPoint) / span).coerceIn(0f, 1f)
            // Mild S-curve contrast boost
            val curved = if (norm < 0.5f) {
                2f * norm * norm
            } else {
                1f - 2f * (1f - norm) * (1f - norm)
            }
            val blended = (norm * 0.45f + curved * 0.55f).coerceIn(0f, 1f)
            (blended * 255f).roundToInt().coerceIn(0, 255)
        }

        val stretched = IntArray(count) { i -> lut[luma[i]] }

        // 3x3 unsharp sharpening pass (4-neighbor Laplacian blend)
        val outPixels = IntArray(count)
        for (y in 0 until h) {
            val row = y * w
            val rowUp = max(0, y - 1) * w
            val rowDown = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val idx = row + x
                val center = stretched[idx]
                val sharpened = if (x in 1 until (w - 1) && y in 1 until (h - 1)) {
                    val neighborsAvg = (
                        stretched[rowUp + x] +
                            stretched[rowDown + x] +
                            stretched[row + x - 1] +
                            stretched[row + x + 1]
                        ) shr 2
                    val delta = center - neighborsAvg
                    (center + (delta * 0.65f).roundToInt()).coerceIn(0, 255)
                } else {
                    center
                }
                outPixels[idx] = (0xFF shl 24) or (sharpened shl 16) or (sharpened shl 8) or sharpened
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun percentileFromHist(hist: IntArray, total: Int, fraction: Float): Int {
        val target = (total * fraction).toInt().coerceIn(0, total - 1)
        var acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= target) return v
        }
        return 255
    }
}
