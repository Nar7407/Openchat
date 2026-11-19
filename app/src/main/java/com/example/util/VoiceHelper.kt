package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class VoiceInputController(
    private val context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    fun toggleListening() {
        if (active) stop() else start()
    }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFailure("Speech recognition service unavailable on this device.")
            return
        }
        destroyRecognizer()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                active = true
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                active = false
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                active = false
                onListeningChanged(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice network error."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice input timed out."
                    else -> "Voice dictation failed (code $error)."
                }
                onFailure(msg)
            }

            override fun onResults(results: Bundle?) {
                active = false
                onListeningChanged(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrEmpty()) onFinalText(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrEmpty()) onPartialText(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        active = true
        onListeningChanged(true)
        sr.startListening(intent)
    }

    fun stop() {
        active = false
        onListeningChanged(false)
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        stop()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}

class ReadAloudController(
    context: Context,
    private val onSpeakingIdChanged: (Long?) -> Unit
) {
    private var ready = false
    private var pendingSpeech: Pair<Long, String>? = null
    private var currentId: Long? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = (status == TextToSpeech.SUCCESS)
        if (ready) {
            ttsRef?.language = Locale.getDefault()
            pendingSpeech?.let { (id, text) ->
                pendingSpeech = null
                speak(id, text)
            }
        }
    }

    private val ttsRef: TextToSpeech? get() = tts

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                currentId = null
                onSpeakingIdChanged(null)
            }

            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String?) {
                currentId = null
                onSpeakingIdChanged(null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                currentId = null
                onSpeakingIdChanged(null)
            }
        })
    }

    fun toggleSpeak(messageId: Long, rawMarkdown: String) {
        if (currentId == messageId) {
            stop()
            return
        }
        speak(messageId, rawMarkdown)
    }

    fun speak(messageId: Long, rawMarkdown: String) {
        val clean = stripMarkdownForSpeech(rawMarkdown)
        if (clean.isBlank()) return
        if (!ready) {
            pendingSpeech = messageId to clean
            return
        }
        currentId = messageId
        onSpeakingIdChanged(messageId)
        val utteranceId = UUID.randomUUID().toString()
        tts.speak(clean.take(3800), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        currentId = null
        pendingSpeech = null
        onSpeakingIdChanged(null)
        runCatching { tts.stop() }
    }

    fun release() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun stripMarkdownForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("[#*_>~|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
