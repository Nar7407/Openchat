package com.example.ui.assistant

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.AllChatApplication
import com.example.MainActivity
import com.example.ui.components.AssistantOverlaySheet
import com.example.ui.theme.AllChatTheme
import com.example.util.EarbudKeyBus
import com.example.widget.AllChatWidgetProvider

/**
 * Standalone translucent Google-Assistant-style overlay Activity launched via:
 * - Android Default Assistant / ASSIST intent
 * - Deep link: allchat://assistant, allchat://live, web+allchat://live
 * - App Shortcut ("Assistant")
 * - Key Mapper / MacroDroid / Bluetooth Earbud shortcut
 */
class AssistantOverlayActivity : ComponentActivity() {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (EarbudKeyBus.handleHardwareKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AllChatApplication

        setContent {
            AllChatTheme {
                AssistantOverlaySheet(
                    repository = app.repository,
                    autoStartMic = true,
                    onOpenTranslateCamera = {
                        val lensIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra(AllChatWidgetProvider.EXTRA_ALLCHAT_ACTION, "lens_translate")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(lensIntent)
                        finish()
                    },
                    onOpenLiveLensExplain = {
                        val lensIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra(AllChatWidgetProvider.EXTRA_ALLCHAT_ACTION, "lens_explain")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(lensIntent)
                        finish()
                    },
                    onOpenFullApp = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onOpenToolkitHub = {
                        val toolkitIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra(AllChatWidgetProvider.EXTRA_ALLCHAT_ACTION, "toolkit")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(toolkitIntent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
