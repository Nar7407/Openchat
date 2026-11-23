package com.example.util

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import java.util.Locale

/**
 * Hooks Android hardware MediaSession events (Bluetooth earbuds Play/Pause & Next-Track tap)
 * to trigger AllChat voice listening or read-aloud hands-free while commuting.
 */
class MediaButtonBridge(
    context: Context,
    private val onEarbudTriggerAsk: () -> Unit,
    private val onEarbudNextAction: () -> Unit = {}
) {
    private var session: MediaSession? = null

    fun start(context: Context) {
        if (session != null) return
        val ms = MediaSession(context.applicationContext, "AllChatEarbudBridge")
        ms.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            onEarbudTriggerAsk()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            onEarbudNextAction()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }

            override fun onPlay() {
                onEarbudTriggerAsk()
            }

            override fun onPause() {
                onEarbudTriggerAsk()
            }

            override fun onSkipToNext() {
                onEarbudNextAction()
            }
        })
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT
            )
            .setState(PlaybackState.STATE_PAUSED, 0L, 1f)
            .build()
        ms.setPlaybackState(state)
        ms.isActive = true
        session = ms
    }

    fun stop() {
        runCatching {
            session?.isActive = false
            session?.release()
        }
        session = null
    }
}

/**
 * Lightweight foreground wake-phrase listener ("Hey AllChat") using Android SpeechRecognizer.
 * Active only while the Assistant or Commute HUD is open and toggled ON by the user.
 */
class WakePhraseListener(
    private val context: Context,
    private val wakePhrase: String = "hey allchat",
    private val onWakeDetected: (trailingCommand: String) -> Unit,
    private val onStatusChanged: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        onStatusChanged("HOTWORD // Listening for \"$wakePhrase\"…")
        restartCycle()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        onStatusChanged("HOTWORD // Off")
    }

    private fun restartCycle() {
        if (!running) return
        runCatching { recognizer?.destroy() }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                checkMatches(partialResults)
            }

            override fun onResults(results: Bundle?) {
                if (!checkMatches(results) && running) {
                    handler.postDelayed({ restartCycle() }, 450L)
                }
            }

            override fun onError(error: Int) {
                if (running) {
                    handler.postDelayed({ restartCycle() }, 900L)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        runCatching { sr.startListening(intent) }
    }

    private fun checkMatches(bundle: Bundle?): Boolean {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val targets = listOf(wakePhrase.lowercase(), "hey all chat", "ok allchat", "ok all chat")
        for (m in matches) {
            val lower = m.lowercase()
            val hit = targets.firstOrNull { lower.contains(it) }
            if (hit != null) {
                val trailing = lower.substringAfter(hit).trim()
                stop()
                onWakeDetected(trailing)
                return true
            }
        }
        return false
    }
}
