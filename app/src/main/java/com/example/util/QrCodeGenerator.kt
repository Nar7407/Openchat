package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream

/**
 * Zero-dependency pure-Kotlin QR Code encoder (ISO/IEC 18004 Byte Mode, Error Correction Level L,
 * Versions 1..10 supporting up to 271 UTF-8 bytes) plus local QR payload parser for UPI/Wi-Fi/URLs.
 */
object QrCodeGenerator {

    sealed interface ParsedQrAction {
        val badge: String
        val title: String
        val subtitle: String
        val raw: String

        data class UpiLink(val payeeName: String, val upiId: String, val amount: String?, override val raw: String) : ParsedQrAction {
            override val badge = "QR // UPI PAYMENT"
            override val title = if (payeeName.isNotBlank()) "Pay $payeeName" else "UPI Payment"
            override val subtitle = buildString {
                append(upiId)
                if (!amount.isNullOrBlank()) append(" · ₹$amount")
            }
        }

        data class WifiNetwork(val ssid: String, val password: String, val security: String, override val raw: String) : ParsedQrAction {
            override val badge = "QR // WI-FI"
            override val title = "Wi-Fi: $ssid"
            override val subtitle = if (password.isNotBlank()) "Pass: $password ($security)" else "Open Network"
        }

        data class WebUrl(val url: String, override val raw: String) : ParsedQrAction {
            override val badge = "QR // LINK"
            override val title = "Open Link"
            override val subtitle = url
        }

        data class PlainText(override val raw: String) : ParsedQrAction {
            override val badge = "QR // TEXT"
            override val title = "Scanned QR Payload"
            override val subtitle = raw.take(120)
        }
    }

    fun parsePayload(rawText: String): ParsedQrAction {
        val clean = rawText.trim()
        val lower = clean.lowercase()
        if (lower.startsWith("upi://pay")) {
            val uri = runCatching { android.net.Uri.parse(clean) }.getOrNull()
            val pa = uri?.getQueryParameter("pa").orEmpty()
            val pn = uri?.getQueryParameter("pn").orEmpty()
            val am = uri?.getQueryParameter("am")
            return ParsedQrAction.UpiLink(pn, pa, am, clean)
        }
        if (clean.startsWith("WIFI:", ignoreCase = true)) {
            val ssid = Regex("""S:([^;]*)""").find(clean)?.groupValues?.getOrNull(1).orEmpty()
            val pass = Regex("""P:([^;]*)""").find(clean)?.groupValues?.getOrNull(1).orEmpty()
            val sec = Regex("""T:([^;]*)""").find(clean)?.groupValues?.getOrNull(1)?.ifBlank { "WPA" } ?: "WPA"
            return ParsedQrAction.WifiNetwork(ssid.ifBlank { "Wi-Fi Network" }, pass, sec, clean)
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return ParsedQrAction.WebUrl(clean, clean)
        }
        return ParsedQrAction.PlainText(clean)
    }

    // Version specs for EC Level L: (version, totalDataCodewords, ecCodewordsPerBlock, numBlocks, alignCenters)
    private data class QrSpec(
        val version: Int,
        val dataCodewords: Int,
        val ecPerBlock: Int,
        val blocks: Int,
        val alignCenters: IntArray
    )

    private val SPECS_L = listOf(
        QrSpec(1, 19, 7, 1, intArrayOf()),
        QrSpec(2, 34, 10, 1, intArrayOf(6, 18)),
        QrSpec(3, 55, 15, 1, intArrayOf(6, 22)),
        QrSpec(4, 80, 20, 1, intArrayOf(6, 26)),
        QrSpec(5, 108, 26, 1, intArrayOf(6, 30)),
        QrSpec(6, 136, 18, 2, intArrayOf(6, 34)),
        QrSpec(7, 156, 20, 2, intArrayOf(6, 22, 38)),
        QrSpec(8, 194, 24, 2, intArrayOf(6, 24, 42)),
        QrSpec(9, 232, 30, 2, intArrayOf(6, 26, 46)),
        QrSpec(10, 274, 18, 4, intArrayOf(6, 28, 50))
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val rem = IntArray(ecLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecLen - 1)
            rem[ecLen - 1] = 0
            for (j in 0 until ecLen) {
                rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return rem
    }

    fun generateBitmap(content: String, pixelSize: Int = 640): Bitmap {
        val bytes = content.toByteArray(Charsets.UTF_8).let {
            if (it.size > 270) it.copyOf(270) else it
        }
        val spec = SPECS_L.firstOrNull { bytes.size + 3 <= it.dataCodewords } ?: SPECS_L.last()
        val matrix = encodeMatrix(bytes, spec)
        val size = matrix.size
        val quiet = 4
        val totalModules = size + quiet * 2
        val scale = (pixelSize / totalModules).coerceAtLeast(4)
        val imgSize = totalModules * scale
        val pixels = IntArray(imgSize * imgSize) { Color.WHITE }

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (matrix[r][c]) {
                    val startY = (r + quiet) * scale
                    val startX = (c + quiet) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (startY + dy) * imgSize + startX
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = Color.BLACK
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, imgSize, imgSize, Bitmap.Config.ARGB_8888)
    }

    fun saveQrPngToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
        val file = File(dir, "allchat_qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private fun encodeMatrix(bytes: ByteArray, spec: QrSpec): Array<BooleanArray> {
        val bits = ArrayList<Boolean>(spec.dataCodewords * 8)
        fun appendBits(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }
        // Mode 0100 (Byte)
        appendBits(0b0100, 4)
        // Character count indicator (8 bits for versions 1..9, 16 bits for version 10+)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(bytes.size, countBits)
        for (b in bytes) appendBits(b.toInt() and 0xFF, 8)

        val capacityBits = spec.dataCodewords * 8
        val termLen = minOf(4, capacityBits - bits.size)
        repeat(termLen) { bits.add(false) }
        while (bits.size % 8 != 0) bits.add(false)

        val dataCodewords = IntArray(spec.dataCodewords)
        var cwIdx = 0
        var i = 0
        while (i < bits.size && cwIdx < dataCodewords.size) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or if (bits[i + b]) 1 else 0
            }
            dataCodewords[cwIdx++] = v
            i += 8
        }
        var padToggle = true
        while (cwIdx < dataCodewords.size) {
            dataCodewords[cwIdx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        // Split into blocks & compute Reed-Solomon ECC
        val blocksCount = spec.blocks
        val baseLen = spec.dataCodewords / blocksCount
        val extraBlocks = spec.dataCodewords % blocksCount
        val dataBlocks = ArrayList<IntArray>(blocksCount)
        val ecBlocks = ArrayList<IntArray>(blocksCount)
        var offset = 0
        for (b in 0 until blocksCount) {
            val len = baseLen + if (b >= blocksCount - extraBlocks) 1 else 0
            val slice = dataCodewords.copyOfRange(offset, offset + len)
            offset += len
            dataBlocks.add(slice)
            ecBlocks.add(rsComputeRemainder(slice, spec.ecPerBlock))
        }

        // Interleave codewords
        val finalStream = ArrayList<Int>(spec.dataCodewords + spec.ecPerBlock * blocksCount)
        val maxDataLen = dataBlocks.maxOf { it.size }
        for (idx in 0 until maxDataLen) {
            for (blk in dataBlocks) {
                if (idx < blk.size) finalStream.add(blk[idx])
            }
        }
        for (idx in 0 until spec.ecPerBlock) {
            for (blk in ecBlocks) {
                finalStream.add(blk[idx])
            }
        }

        val dim = spec.version * 4 + 17
        val modules = Array(dim) { BooleanArray(dim) }
        val isFunction = Array(dim) { BooleanArray(dim) }

        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until dim && c in 0 until dim) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        // Finder patterns + separators
        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until dim || c !in 0 until dim) continue
                    val dark = (dr in 0..6 && dc in 0..6) && (
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                        )
                    setFunc(r, c, dark)
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(0, dim - 7)
        drawFinder(dim - 7, 0)

        // Timing patterns
        for (k in 8 until dim - 8) {
            setFunc(6, k, k % 2 == 0)
            setFunc(k, 6, k % 2 == 0)
        }

        // Alignment patterns
        val centers = spec.alignCenters
        for (rCenter in centers) {
            for (cCenter in centers) {
                val overlapsFinder =
                    (rCenter == 6 && cCenter == 6) ||
                        (rCenter == 6 && cCenter == centers.last()) ||
                        (rCenter == centers.last() && cCenter == 6)
                if (overlapsFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        setFunc(rCenter + dr, cCenter + dc, dark)
                    }
                }
            }
        }

        // Reserve format info + dark module
        setFunc(dim - 8, 8, true)
        for (k in 0..8) {
            if (k != 6) {
                isFunction[8][k] = true
                isFunction[k][8] = true
            }
        }
        for (k in 0..7) {
            isFunction[8][dim - 1 - k] = true
            isFunction[dim - 1 - k][8] = true
        }

        // Reserve version info for version >= 7
        if (spec.version >= 7) {
            val vBits = computeVersionInfo(spec.version)
            for (bitIdx in 0 until 18) {
                val bit = ((vBits ushr bitIdx) and 1) != 0
                val a = dim - 11 + (bitIdx % 3)
                val b = bitIdx / 3
                setFunc(a, b, bit)
                setFunc(b, a, bit)
            }
        }

        // Place data bits in zig-zag columns
        var bitCursor = 0
        val totalBits = finalStream.size * 8
        var col = dim - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until dim) {
                val row = if (upward) dim - 1 - step else step
                for (dc in 0..1) {
                    val c = col - dc
                    if (!isFunction[row][c]) {
                        val dark = if (bitCursor < totalBits) {
                            val cw = finalStream[bitCursor / 8]
                            ((cw ushr (7 - (bitCursor % 8))) and 1) != 0
                        } else {
                            false
                        }
                        bitCursor++
                        // Apply mask pattern 0: (row + col) % 2 == 0
                        val masked = if ((row + c) % 2 == 0) !dark else dark
                        modules[row][c] = masked
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        // Write format information for EC Level L (01) + Mask 0 (000) -> 5-bit data = 0b01000
        val fmtBits = computeFormatInfo(ecBits = 0b01, mask = 0)
        for (k in 0..5) modules[8][k] = ((fmtBits ushr (14 - k)) and 1) != 0
        modules[8][7] = ((fmtBits ushr 8) and 1) != 0
        modules[8][8] = ((fmtBits ushr 7) and 1) != 0
        modules[7][8] = ((fmtBits ushr 6) and 1) != 0
        for (k in 0..5) modules[5 - k][8] = ((fmtBits ushr (5 - k)) and 1) != 0

        for (k in 0..6) modules[dim - 1 - k][8] = ((fmtBits ushr (14 - k)) and 1) != 0
        for (k in 0..7) modules[8][dim - 8 + k] = ((fmtBits ushr (7 - k)) and 1) != 0

        return modules
    }

    private fun computeFormatInfo(ecBits: Int, mask: Int): Int {
        val data = (ecBits shl 3) or mask
        var rem = data shl 10
        val gen = 0b10100110111
        for (i in 14 downTo 10) {
            if (((rem ushr i) and 1) != 0) {
                rem = rem xor (gen shl (i - 10))
            }
        }
        return ((data shl 10) or rem) xor 0b101010000010010
    }

    private fun computeVersionInfo(version: Int): Int {
        var rem = version shl 12
        val gen = 0b1111100100101
        for (i in 17 downTo 12) {
            if (((rem ushr i) and 1) != 0) {
                rem = rem xor (gen shl (i - 12))
            }
        }
        return (version shl 12) or rem
    }
}
