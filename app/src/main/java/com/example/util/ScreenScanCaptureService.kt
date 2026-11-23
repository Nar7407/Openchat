package com.example.util

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.PixelCopy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Single-frame Android MediaProjection foreground service (`foregroundServiceType="mediaProjection"`).
 * Captures one frame of the active screen/app via VirtualDisplay + ImageReader, delivers the Bitmap
 * to [ScreenScanCaptureBus], and immediately tears down projection and stops itself.
 */
object ScreenScanCaptureBus {
    @Volatile
    var onCapturedBitmap: ((Bitmap?) -> Unit)? = null

    fun deliver(bitmap: Bitmap?) {
        val cb = onCapturedBitmap
        onCapturedBitmap = null
        Handler(Looper.getMainLooper()).post {
            cb?.invoke(bitmap)
        }
    }

    /**
     * Fast zero-dialog capture of the current active Activity window using PixelCopy (API 26+)
     * with View.draw fallback.
     */
    fun captureActiveWindow(activity: Activity, onResult: (Bitmap?) -> Unit) {
        val window = activity.window ?: return onResult(null)
        val decor = window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) {
            onResult(null)
            return
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onResult(bitmap)
                        } else {
                            drawViewFallback(decor, bitmap, onResult)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }.onFailure {
                drawViewFallback(decor, bitmap, onResult)
            }
        } else {
            drawViewFallback(decor, bitmap, onResult)
        }
    }

    private fun drawViewFallback(view: android.view.View, bitmap: Bitmap, onResult: (Bitmap?) -> Unit) {
        runCatching {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            onResult(bitmap)
        }.onFailure {
            onResult(null)
        }
    }
}

class ScreenScanCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "allchat_screen_scan"
        private const val NOTIFICATION_ID = 4109
        const val EXTRA_RESULT_CODE = "extra_mp_result_code"
        const val EXTRA_RESULT_DATA = "extra_mp_result_data"

        fun captureSingleFrame(
            context: Context,
            resultCode: Int,
            data: Intent,
            onCaptured: (Bitmap?) -> Unit
        ) {
            ScreenScanCaptureBus.onCapturedBitmap = onCaptured
            val svcIntent = Intent(context.applicationContext, ScreenScanCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, svcIntent)
            }.onFailure {
                ScreenScanCaptureBus.deliver(null)
            }
        }
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            ScreenScanCaptureBus.deliver(null)
            finishAndStop(startId)
            return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val mp = runCatching { mpm?.getMediaProjection(resultCode, data) }.getOrNull()
        if (mp == null) {
            ScreenScanCaptureBus.deliver(null)
            finishAndStop(startId)
            return START_NOT_STICKY
        }
        projection = mp

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels / 2).coerceAtLeast(540)
        val height = (metrics.heightPixels / 2).coerceAtLeast(960)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        var captured = false
        val timeoutRunnable = Runnable {
            if (!captured) {
                captured = true
                ScreenScanCaptureBus.deliver(null)
                finishAndStop(startId)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 4000L)

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!captured) {
                    captured = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    ScreenScanCaptureBus.deliver(null)
                    finishAndStop(startId)
                }
            }
        }, mainHandler)

        reader.setOnImageAvailableListener({ r ->
            if (captured) return@setOnImageAvailableListener
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            captured = true
            mainHandler.removeCallbacks(timeoutRunnable)
            val bmp = runCatching {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val rawBmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                rawBmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(rawBmp, 0, 0, width, height)
                if (cropped != rawBmp) rawBmp.recycle()
                cropped
            }.getOrNull()
            image.close()
            ScreenScanCaptureBus.deliver(bmp)
            finishAndStop(startId)
        }, mainHandler)

        // Small 280ms delay lets the permission dialog finish dismissing before grabbing the clean frame
        mainHandler.postDelayed({
            if (!captured) {
                runCatching {
                    virtualDisplay = mp.createVirtualDisplay(
                        "AllChatScreenScan",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface,
                        null,
                        mainHandler
                    )
                }.onFailure {
                    if (!captured) {
                        captured = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        ScreenScanCaptureBus.deliver(null)
                        finishAndStop(startId)
                    }
                }
            }
        }, 280L)

        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Screen Scan", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    }
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AllChat Screen Scan")
            .setContentText("Capturing single frame for AI vision…")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun finishAndStop(startId: Int) {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.stop() }
        projection = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf(startId)
    }
}
