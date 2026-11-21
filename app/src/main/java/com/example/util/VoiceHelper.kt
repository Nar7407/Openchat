package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val onFailure: (String) -> Unit,
    private val onRmsChanged: (Float) -> Unit = {}
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
            override fun onRmsChanged(rmsdB: Float) {
                this@VoiceInputController.onRmsChanged.invoke(rmsdB)
            }

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
    private val onSpeakingIdChanged: (Long?) -> Unit,
    private val onQueueDrained: () -> Unit = {}
) {
    private var ready = false
    private var pendingSpeech: Pair<Long, String>? = null
    private val pendingQueue = mutableListOf<String>()
    private val activeUtteranceIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var currentId: Long? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = (status == TextToSpeech.SUCCESS)
        if (ready) {
            ttsRef?.language = Locale.getDefault()
            pendingSpeech?.let { (id, text) ->
                pendingSpeech = null
                speak(id, text)
            }
            if (pendingQueue.isNotEmpty()) {
                val queued = pendingQueue.toList()
                pendingQueue.clear()
                queued.forEach { enqueueSentence(it) }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ttsRef: TextToSpeech? get() = tts

    private fun handleUtteranceFinished(utteranceId: String?) {
        if (utteranceId != null) activeUtteranceIds.remove(utteranceId)
        if (activeUtteranceIds.isEmpty()) {
            currentId = null
            mainHandler.post {
                onSpeakingIdChanged(null)
                onQueueDrained()
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handleUtteranceFinished(utteranceId)
            }

            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String?) {
                handleUtteranceFinished(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleUtteranceFinished(utteranceId)
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
        activeUtteranceIds.clear()
        currentId = messageId
        onSpeakingIdChanged(messageId)
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceIds.add(utteranceId)
        tts.speak(clean.take(3800), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun enqueueSentence(rawSentence: String) {
        val clean = stripMarkdownForSpeech(rawSentence)
        if (clean.isBlank()) return
        if (!ready) {
            pendingQueue += clean
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceIds.add(utteranceId)
        currentId = -1L
        onSpeakingIdChanged(-1L)
        tts.speak(clean.take(1500), TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun hasActiveSpeech(): Boolean = activeUtteranceIds.isNotEmpty() || pendingQueue.isNotEmpty()

    fun stop() {
        currentId = null
        pendingSpeech = null
        pendingQueue.clear()
        activeUtteranceIds.clear()
        onSpeakingIdChanged(null)
        runCatching { tts.stop() }
    }

    fun release() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun stripMarkdownForSpeech(input: String): String {
        return input
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("[#*_>~|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
