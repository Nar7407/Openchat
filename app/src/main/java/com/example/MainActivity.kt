package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.domain.model.IncomingAppAction
import com.example.domain.model.IncomingSharePayload
import com.example.ui.AllChatApp
import com.example.ui.theme.AllChatTheme
import com.example.util.EarbudKeyBus
import com.example.widget.AllChatWidgetProvider

class MainActivity : ComponentActivity() {

    private val pendingSharePayload = mutableStateOf<IncomingSharePayload?>(null)
    private val pendingAppAction = mutableStateOf<IncomingAppAction?>(null)
    private var lastVolUpDownMs: Long = 0L
    private var lastVolDownDownMs: Long = 0L

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (EarbudKeyBus.handleHardwareKeyEvent(event)) {
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = SystemClock.uptimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    lastVolUpDownMs = now
                    if (now - lastVolDownDownMs in 0L..300L) {
                        lastVolUpDownMs = 0L
                        lastVolDownDownMs = 0L
                        pendingAppAction.value = IncomingAppAction.ASSISTANT_OVERLAY
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    lastVolDownDownMs = now
                    if (now - lastVolUpDownMs in 0L..300L) {
                        lastVolUpDownMs = 0L
                        lastVolDownDownMs = 0L
                        pendingAppAction.value = IncomingAppAction.ASSISTANT_OVERLAY
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 0. Check custom protocol deep links (allchat://, web+allchat://, or ?mode=live)
        val dataUri = intent.data
        val scheme = dataUri?.scheme?.lowercase()
        val queryMode = dataUri?.getQueryParameter("mode")?.lowercase().orEmpty()
        if (queryMode == "live") {
            pendingAppAction.value = IncomingAppAction.ASSISTANT_OVERLAY
            return
        }
        if (dataUri != null && (scheme == "allchat" || scheme == "web+allchat")) {
            val host = dataUri.host?.lowercase().orEmpty()
            val path = dataUri.path?.lowercase().orEmpty()
            val modeParam = queryMode
            val queryParam = dataUri.getQueryParameter("q")?.trim().orEmpty()
            when {
                host == "ask" && queryParam.isNotBlank() -> {
                    pendingSharePayload.value = IncomingSharePayload.DeepLinkAsk(queryParam)
                    return
                }
                host == "live" || path == "/live" || modeParam == "live" || modeParam == "assistant" || host == "assistant" -> {
                    pendingAppAction.value = IncomingAppAction.ASSISTANT_OVERLAY
                    return
                }
                host == "lens" || modeParam.startsWith("lens") -> {
                    pendingAppAction.value = when (modeParam) {
                        "translate" -> IncomingAppAction.LIVE_LENS_TRANSLATE
                        "solve" -> IncomingAppAction.LIVE_LENS_SOLVE
                        "code", "diagram" -> IncomingAppAction.LIVE_LENS_CODE
                        else -> IncomingAppAction.LIVE_LENS_EXPLAIN
                    }
                    return
                }
                host == "commute" || modeParam == "commute" -> {
                    pendingAppAction.value = IncomingAppAction.COMMUTE_HUD
                    return
                }
                host == "interpreter" || host == "interpret" || modeParam == "interpreter" -> {
                    pendingAppAction.value = IncomingAppAction.INTERPRETER
                    return
                }
                host == "toolkit" || modeParam == "toolkit" -> {
                    pendingAppAction.value = IncomingAppAction.TOOLKIT
                    return
                }
            }
        }

        // 0b. OS-Level "Open With AllChat" File Association (ACTION_VIEW with content:// or file://)
        if (intent.action == Intent.ACTION_VIEW && dataUri != null && (scheme == "content" || scheme == "file")) {
            val mime = (intent.type ?: contentResolver.getType(dataUri))?.lowercase().orEmpty()
            when {
                mime.startsWith("image/") -> {
                    pendingSharePayload.value = IncomingSharePayload.ImageUri(dataUri.toString())
                }
                else -> {
                    pendingSharePayload.value = IncomingSharePayload.DocumentUri(dataUri.toString())
                }
            }
            return
        }

        // 1. Check App Shortcut / Home Screen Widget quick action
        val actionExtra = intent.getStringExtra(AllChatWidgetProvider.EXTRA_ALLCHAT_ACTION)?.lowercase()
        if (!actionExtra.isNullOrBlank()) {
            pendingAppAction.value = when (actionExtra) {
                "new" -> IncomingAppAction.NEW_CHAT
                "voice" -> IncomingAppAction.VOICE_PROMPT
                "clipboard" -> IncomingAppAction.SUMMARIZE_CLIPBOARD
                "image" -> IncomingAppAction.GENERATE_IMAGE
                "camera" -> IncomingAppAction.CAMERA_VISION
                "assistant", "live" -> IncomingAppAction.ASSISTANT_OVERLAY
                "lens" -> IncomingAppAction.LIVE_LENS_EXPLAIN
                "lens_translate" -> IncomingAppAction.LIVE_LENS_TRANSLATE
                "commute" -> IncomingAppAction.COMMUTE_HUD
                "interpreter" -> IncomingAppAction.INTERPRETER
                "toolkit" -> IncomingAppAction.TOOLKIT
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
