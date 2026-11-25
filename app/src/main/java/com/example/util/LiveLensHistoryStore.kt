package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class LiveLensHistoryEntry(
    val id: Long,
    val timestampMs: Long,
    val modeName: String,
    val targetLanguage: String,
    val modelLabel: String,
    val answer: String,
    val scanEnhanced: Boolean,
    val wasAutoCropped: Boolean,
    val imageFilePath: String
)

/**
 * Persistent on-device store for the last 10 Live Lens captures (JPEG frame + extracted OCR/AI answer)
 * so users can tap any thumbnail in the Live Lens history strip to re-inspect or re-run an earlier scan.
 */
object LiveLensHistoryStore {

    private const val DIR_NAME = "live_lens_history"
    private const val INDEX_FILE = "history.json"
    private const val MAX_ENTRIES = 10

    private fun historyDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    private fun indexFile(context: Context): File =
        File(historyDir(context), INDEX_FILE)

    suspend fun loadAll(context: Context): List<LiveLensHistoryEntry> = withContext(Dispatchers.IO) {
        val file = indexFile(context)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("imageFilePath")
                    if (path.isNotBlank() && File(path).exists()) {
                        add(
                            LiveLensHistoryEntry(
                                id = obj.optLong("id", System.currentTimeMillis() - i),
                                timestampMs = obj.optLong("timestampMs", 0L),
                                modeName = obj.optString("modeName", "AUTO_EXPLAIN"),
                                targetLanguage = obj.optString("targetLanguage", "English"),
                                modelLabel = obj.optString("modelLabel", "VISION"),
                                answer = obj.optString("answer", ""),
                                scanEnhanced = obj.optBoolean("scanEnhanced", false),
                                wasAutoCropped = obj.optBoolean("wasAutoCropped", false),
                                imageFilePath = path
                            )
                        )
                    }
                }
            }.sortedByDescending { it.timestampMs }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    suspend fun saveEntry(
        context: Context,
        bitmap: Bitmap,
        modeName: String,
        targetLanguage: String,
        modelLabel: String,
        answer: String,
        scanEnhanced: Boolean,
        wasAutoCropped: Boolean
    ): List<LiveLensHistoryEntry> = withContext(Dispatchers.IO) {
        val dir = historyDir(context)
        val now = System.currentTimeMillis()
        val imgFile = File(dir, "frame_$now.jpg")

        val scaled = scaleForStorage(bitmap, maxEdge = 1080)
        runCatching {
            FileOutputStream(imgFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 84, fos)
            }
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val newEntry = LiveLensHistoryEntry(
            id = now,
            timestampMs = now,
            modeName = modeName,
            targetLanguage = targetLanguage,
            modelLabel = modelLabel,
            answer = answer,
            scanEnhanced = scanEnhanced,
            wasAutoCropped = wasAutoCropped,
            imageFilePath = imgFile.absolutePath
        )

        val current = loadAll(context)
        val combined = (listOf(newEntry) + current).take(MAX_ENTRIES)
        val keepPaths = combined.map { it.imageFilePath }.toSet()

        // Prune older frame files beyond the latest 10
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("frame_") && f.name.endsWith(".jpg") && f.absolutePath !in keepPaths) {
                runCatching { f.delete() }
            }
        }

        writeIndex(context, combined)
        combined
    }

    suspend fun deleteEntry(context: Context, id: Long): List<LiveLensHistoryEntry> = withContext(Dispatchers.IO) {
        val current = loadAll(context)
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.imageFilePath).delete() }
        }
        val remaining = current.filterNot { it.id == id }
        writeIndex(context, remaining)
        remaining
    }

    suspend fun clearAll(context: Context): List<LiveLensHistoryEntry> = withContext(Dispatchers.IO) {
        val dir = historyDir(context)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        emptyList()
    }

    suspend fun loadBitmap(entry: LiveLensHistoryEntry, maxEdge: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(entry.imageFilePath)
        if (!file.exists()) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (longest / (sample * 2) >= maxEdge) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun writeIndex(context: Context, entries: List<LiveLensHistoryEntry>) {
        val arr = JSONArray()
        entries.take(MAX_ENTRIES).forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("timestampMs", e.timestampMs)
                    .put("modeName", e.modeName)
                    .put("targetLanguage", e.targetLanguage)
                    .put("modelLabel", e.modelLabel)
                    .put("answer", e.answer)
                    .put("scanEnhanced", e.scanEnhanced)
                    .put("wasAutoCropped", e.wasAutoCropped)
                    .put("imageFilePath", e.imageFilePath)
            )
        }
        runCatching { indexFile(context).writeText(arr.toString()) }
    }

    private fun scaleForStorage(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = max(w, h)
        if (longest <= maxEdge || w <= 0 || h <= 0) return src
        val ratio = maxEdge.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (w * ratio).roundToInt().coerceAtLeast(1),
            (h * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }
}
