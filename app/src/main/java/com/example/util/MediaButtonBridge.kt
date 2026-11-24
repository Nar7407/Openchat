package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

enum class EarbudCommand {
    SINGLE_TAP,
    DOUBLE_TAP_OR_NEXT
}

/**
 * Process-wide hardware media button bus so Activity.dispatchKeyEvent and MediaSession
 * can cleanly deliver single-tap (Confirm / Barge-In / Toggle Mic / Open Live Assistant)
 * and double-tap / next-track (Cancel pending action) events.
 */
object EarbudKeyBus {
    private val _events = MutableSharedFlow<EarbudCommand>(extraBufferCapacity = 8)
    val events: SharedFlow<EarbudCommand> = _events.asSharedFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSingleTapRunnable: Runnable? = null
    private const val DOUBLE_TAP_WINDOW_MS = 320L

    fun emitSingleOrDetectDoubleTap() {
        val existing = pendingSingleTapRunnable
        if (existing != null) {
            mainHandler.removeCallbacks(existing)
            pendingSingleTapRunnable = null
            _events.tryEmit(EarbudCommand.DOUBLE_TAP_OR_NEXT)
        } else {
            val r = Runnable {
                pendingSingleTapRunnable = null
                _events.tryEmit(EarbudCommand.SINGLE_TAP)
            }
            pendingSingleTapRunnable = r
            mainHandler.postDelayed(r, DOUBLE_TAP_WINDOW_MS)
        }
    }

    fun emitCancelOrNext() {
        pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTapRunnable = null
        _events.tryEmit(EarbudCommand.DOUBLE_TAP_OR_NEXT)
    }

    /**
     * Intercept hardware Bluetooth earbud / headset media keys in Activity.dispatchKeyEvent.
     */
    fun handleHardwareKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                emitSingleOrDetectDoubleTap()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                emitCancelOrNext()
                true
            }
            else -> false
        }
    }
}

/**
 * Synthesizes subtle, pleasant AudioTrack chimes directly in memory (zero raw audio files required)
 * so Bluetooth earbud users get immediate tactile audio confirmation without looking at the screen.
 */
object EarbudChimePlayer {
    private const val SAMPLE_RATE = 22050

    /** Subtle ascending dual-tone chime (660 Hz -> 880 Hz, 145ms) when Live Mic starts listening. */
    fun playMicActivationChime() {
        playToneSequence(listOf(660.0 to 65, 880.0 to 80), gain = 0.22f)
    }

    /** Crisp confirmation chime (784 Hz -> 1046 Hz) when confirming Call/WhatsApp via earbud. */
    fun playConfirmChime() {
        playToneSequence(listOf(784.0 to 60, 1046.5 to 90), gain = 0.22f)
    }

    /** Subtle descending tone (587 Hz -> 440 Hz) when cancelling via earbud double-tap. */
    fun playCancelChime() {
        playToneSequence(listOf(587.3 to 65, 440.0 to 85), gain = 0.20f)
    }

    private fun playToneSequence(notes: List<Pair<Double, Int>>, gain: Float) {
        Thread {
            runCatching {
                val totalSamples = notes.sumOf { (SAMPLE_RATE * it.second) / 1000 }
                if (totalSamples <= 0) return@runCatching
                val pcm = ShortArray(totalSamples)
                var offset = 0
                for ((freqHz, durationMs) in notes) {
                    val count = (SAMPLE_RATE * durationMs) / 1000
                    for (i in 0 until count) {
                        val t = i.toDouble() / SAMPLE_RATE.toDouble()
                        // Smooth Hann-style attack/release envelope to avoid speaker clicks
                        val env = when {
                            i < count * 0.15 -> (i.toDouble() / (count * 0.15))
                            i > count * 0.78 -> ((count - i).toDouble() / (count * 0.22)).coerceAtLeast(0.0)
                            else -> 1.0
                        }
                        val sample = sin(2.0 * PI * freqHz * t) * env * gain * Short.MAX_VALUE
                        pcm[offset + i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    offset += count
                }
                val bytesLen = pcm.size * 2
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bytesLen)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val sleepMs = notes.sumOf { it.second }.toLong() + 80L
                Thread.sleep(sleepMs)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.start()
    }
}

/**
 * Hooks Android hardware MediaSession events (Bluetooth earbuds Play/Pause, HeadsetHook, Next/Prev)
 * with a lightweight silent AudioTrack keep-alive loop so Android OS routes Bluetooth earbud
 * button presses directly to AllChat instead of background music apps.
 */
class MediaButtonBridge(
    context: Context,
    private val onEarbudTriggerAsk: () -> Unit,
    private val onEarbudNextAction: () -> Unit = {},
    private val enableSilentKeepAlive: Boolean = true
) {
    private var session: MediaSession? = null
    private var silentTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSingleTap: Runnable? = null

    private fun triggerSingleOrDoubleTap() {
        val existing = pendingSingleTap
        if (existing != null) {
            mainHandler.removeCallbacks(existing)
            pendingSingleTap = null
            mainHandler.post { onEarbudNextAction() }
        } else {
            val r = Runnable {
                pendingSingleTap = null
                onEarbudTriggerAsk()
            }
            pendingSingleTap = r
            mainHandler.postDelayed(r, 320L)
        }
    }

    private fun triggerImmediateCancelNext() {
        pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTap = null
        mainHandler.post { onEarbudNextAction() }
    }

    fun start(context: Context) {
        if (session != null) return
        if (enableSilentKeepAlive) {
            startSilentKeepAlive()
        }
        val ms = MediaSession(context.applicationContext, "AllChatEarbudBridge")
        ms.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent?.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            triggerSingleOrDoubleTap()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            triggerImmediateCancelNext()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }

            override fun onPlay() {
                triggerSingleOrDoubleTap()
            }

            override fun onPause() {
                triggerSingleOrDoubleTap()
            }

            override fun onSkipToNext() {
                triggerImmediateCancelNext()
            }

            override fun onSkipToPrevious() {
                triggerImmediateCancelNext()
            }
        })
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_STOP
            )
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        ms.setPlaybackState(state)
        ms.isActive = true
        session = ms
    }

    private fun startSilentKeepAlive() {
        if (silentTrack != null) return
        runCatching {
            val sampleRate = 8000
            val frames = sampleRate // 1 second of zeroed PCM silence looped cleanly
            val silentBuffer = ShortArray(frames)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(frames * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(silentBuffer, 0, frames)
            track.setLoopPoints(0, frames, -1)
            track.setVolume(0f)
            track.play()
            silentTrack = track
        }
    }

    fun stop() {
        pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTap = null
        runCatching {
            silentTrack?.stop()
            silentTrack?.release()
        }
        silentTrack = null
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
