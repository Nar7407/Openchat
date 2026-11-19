package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.domain.model.IncomingAppAction
import com.example.domain.model.IncomingSharePayload
import com.example.ui.AllChatApp
import com.example.ui.theme.AllChatTheme
import com.example.widget.AllChatWidgetProvider

class MainActivity : ComponentActivity() {

    private val pendingSharePayload = mutableStateOf<IncomingSharePayload?>(null)
    private val pendingAppAction = mutableStateOf<IncomingAppAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        handleIncomingIntent(intent)

        val app = application as AllChatApplication
        setContent {
            AllChatTheme {
                AllChatApp(
                    repository = app.repository,
                    incomingSharePayload = pendingSharePayload.value,
                    onSharedPayloadConsumed = { pendingSharePayload.value = null },
                    incomingAppAction = pendingAppAction.value,
                    onAppActionConsumed = { pendingAppAction.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check App Shortcut / Home Screen Widget quick action
        val actionExtra = intent.getStringExtra(AllChatWidgetProvider.EXTRA_ALLCHAT_ACTION)?.lowercase()
        if (!actionExtra.isNullOrBlank()) {
            pendingAppAction.value = when (actionExtra) {
                "new" -> IncomingAppAction.NEW_CHAT
                "voice" -> IncomingAppAction.VOICE_PROMPT
                "clipboard" -> IncomingAppAction.SUMMARIZE_CLIPBOARD
                "image" -> IncomingAppAction.GENERATE_IMAGE
                "camera" -> IncomingAppAction.CAMERA_VISION
                else -> null
            }
            return
        }

        // 2. Check Universal Android Share Target (ACTION_SEND)
        if (intent.action != Intent.ACTION_SEND) return
        val mime = intent.type?.lowercase().orEmpty()
        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        when {
            mime.startsWith("image/") && streamUri != null -> {
                pendingSharePayload.value = IncomingSharePayload.ImageUri(streamUri.toString())
            }
            streamUri != null && (
                mime == "application/pdf" ||
                    mime.startsWith("text/") ||
                    mime == "application/msword" ||
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    mime == "application/octet-stream"
                ) -> {
                pendingSharePayload.value = IncomingSharePayload.DocumentUri(streamUri.toString())
            }
            else -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    pendingSharePayload.value = IncomingSharePayload.Text(sharedText)
                }
            }
        }
    }
}
