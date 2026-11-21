package com.example.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AllChatApplication
import com.example.R
import kotlinx.coroutines.launch

/**
 * Short-lived Foreground Service (`shortService`) that runs Home Screen Widget AI requests
 * while preserving full foreground DNS/network access after [WidgetPromptActivity] closes.
 *
 * Prevents Android 12+ / OEM battery savers (ColorOS, MIUI, OneUI) from freezing the app
 * process or blocking DNS (`UnknownHostException`) mid-request when the user is back on the
 * Home Screen.
 */
class WidgetQuickAskService : Service() {

    companion object {
        private const val CHANNEL_ID = "allchat_widget_quick_ask"
        private const val NOTIFICATION_ID = 4107
        const val EXTRA_QUESTION = "extra_widget_question"
        const val ACTION_STOP = "com.example.widget.ACTION_STOP_QUICK_ASK"

        fun startForQuestion(context: Context, question: String) {
            val clean = question.trim()
            if (clean.isEmpty()) return

            // Persist the initial "Thinking…" state immediately so the widget updates instantaneously
            val token = WidgetQuickAskRunner.prepareQuestionState(context, clean)

            val intent = Intent(context.applicationContext, WidgetQuickAskService::class.java).apply {
                putExtra(EXTRA_QUESTION, clean)
                putExtra("extra_request_token", token)
            }
            val started = runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
                true
            }.getOrDefault(false)

            // Fallback if OEM restricts startForegroundService in an unusual state
            if (!started) {
                WidgetQuickAskRunner.launchQuestionInScope(
                    context = context.applicationContext,
                    question = clean,
                    preparedToken = token
                )
            }
        }

        fun stopIfRunning(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, WidgetQuickAskService::class.java)
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishForegroundAndStop(startId)
            return START_NOT_STICKY
        }

        val question = intent?.getStringExtra(EXTRA_QUESTION)?.trim().orEmpty()
        val preparedToken = intent?.getLongExtra("extra_request_token", 0L) ?: 0L

        promoteToForeground(question)

        if (question.isEmpty()) {
            finishForegroundAndStop(startId)
            return START_NOT_STICKY
        }

        val app = application as? AllChatApplication
        if (app == null) {
            finishForegroundAndStop(startId)
            return START_NOT_STICKY
        }

        val token = if (preparedToken > 0L) {
            preparedToken
        } else {
            WidgetQuickAskRunner.prepareQuestionState(applicationContext, question)
        }

        app.applicationScope.launch {
            try {
                WidgetQuickAskRunner.executePreparedQuestion(
                    context = applicationContext,
                    question = question,
                    requestToken = token
                )
            } finally {
                // Only tear down the service if no newer widget request has superseded this one
                if (!WidgetQuickAskRunner.isRequestActive(token)) {
                    // Another request took over — leave service alive for the newer request
                } else {
                    WidgetQuickAskRunner.markTokenFinished(token)
                    finishForegroundAndStop(startId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        WidgetQuickAskRunner.failActiveAsTimeout(applicationContext)
        finishForegroundAndStop(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        WidgetQuickAskRunner.failActiveAsTimeout(applicationContext)
        finishForegroundAndStop(startId)
    }

    private fun promoteToForeground(question: String) {
        ensureNotificationChannel()
        val preview = question.take(48).ifBlank { "Fetching AI answer…" }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AllChat Quick Ask")
            .setContentText(preview)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widget Quick Ask",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Home Screen Widget AI requests connected on Wi-Fi and mobile data."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun finishForegroundAndStop(startId: Int) {
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
