package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.MainActivity
import com.example.R

class AllChatWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_COPY_ANSWER -> {
                val state = WidgetQuickAskRunner.readState(context)
                if (state.answer.isNotBlank()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("AllChat Answer", state.answer))
                    Toast.makeText(context, "Answer copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_CLEAR_ANSWER -> {
                WidgetQuickAskRunner.clearState(context)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val EXTRA_ALLCHAT_ACTION = "allchat_action"
        const val ACTION_COPY_ANSWER = "com.example.widget.ACTION_COPY_ANSWER"
        const val ACTION_CLEAR_ANSWER = "com.example.widget.ACTION_CLEAR_ANSWER"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AllChatWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_allchat_quick)
            val state = WidgetQuickAskRunner.readState(context)

            // 1. ASK & MIC open the translucent floating micro-overlay right over Home Screen (NO full app launch!)
            views.setOnClickPendingIntent(
                R.id.widget_btn_ask,
                buildOverlayPendingIntent(context, WidgetPromptActivity.MODE_TEXT, 2001)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_voice,
                buildOverlayPendingIntent(context, WidgetPromptActivity.MODE_VOICE, 2002)
            )

            // 2. CAM & IMG keep launching Camera Vision and Image Studio as before
            views.setOnClickPendingIntent(
                R.id.widget_btn_camera,
                buildMainActionPendingIntent(context, "camera", 1003)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_image,
                buildMainActionPendingIntent(context, "image", 1004)
            )

            // 3. Direct widget COPY & CLEAR broadcast actions (stay right on Home Screen)
            views.setOnClickPendingIntent(
                R.id.widget_btn_copy,
                buildBroadcastPendingIntent(context, ACTION_COPY_ANSWER, 3001)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_clear,
                buildBroadcastPendingIntent(context, ACTION_CLEAR_ANSWER, 3002)
            )

            // 4. Render status badge & inline Q&A response right on the widget
            val badgeText = when {
                state.isLoading -> "STREAMING…"
                state.isError -> "ERROR"
                state.modelLabel.isNotBlank() -> state.modelLabel.uppercase().take(18)
                else -> "BYOK"
            }
            views.setTextViewText(R.id.widget_status_badge, badgeText)

            if (state.hasContent) {
                views.setViewVisibility(R.id.widget_answer_container, View.VISIBLE)
                val turnCount = state.turns.count { it.role == "USER" }.coerceAtLeast(1)
                views.setTextViewText(
                    R.id.widget_question_text,
                    "THREAD ($turnCount) // ${state.question}"
                )
                views.setTextViewText(R.id.widget_answer_text, state.formattedTranscript())
                views.setTextViewText(
                    R.id.widget_btn_ask,
                    if (state.isLoading) "ASKING…" else "FOLLOW UP…"
                )
            } else {
                views.setViewVisibility(R.id.widget_answer_container, View.GONE)
                views.setTextViewText(R.id.widget_btn_ask, "ASK ANYTHING…")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildOverlayPendingIntent(
            context: Context,
            mode: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, WidgetPromptActivity::class.java).apply {
                action = "com.example.widget.OVERLAY_${mode.uppercase()}"
                putExtra(WidgetPromptActivity.EXTRA_MODE, mode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }

        private fun buildMainActionPendingIntent(
            context: Context,
            actionValue: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.action.WIDGET_${actionValue.uppercase()}"
                putExtra(EXTRA_ALLCHAT_ACTION, actionValue)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }

        private fun buildBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, AllChatWidgetProvider::class.java).apply {
                this.action = action
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }
    }
}
