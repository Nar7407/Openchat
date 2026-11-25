package com.example.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceDspTelemetry(
    val isActive: Boolean = false,
    val levelNormalized: Float = 0f,
    val hardwareNsAttached: Boolean = false,
    val hardwareAgcAttached: Boolean = false,
    val highPassCutoffHz: Int = 120,
    val noiseReductionDb: Float = 0f
)

/**
 * Zero-overhead real-time DSP audio input chain for Voice Notes & Recorder.
 *
 * Routes microphone PCM through:
 *  1. Hardware AcousticEchoCanceler + NoiseSuppressor + AutomaticGainControl (when supported)
 *  2. 2nd-order Butterworth Biquad High-Pass Filter at 120 Hz (cuts fan, AC, wind, and desk rumble)
 *  3. Real-time Dynamics Compressor (-24 dBFS threshold, 4:1 ratio, +6 dB makeup gain) to normalize quiet speech
 */
class VoiceDspAudioChain(
    private val sampleRateHz: Int = 16_000,
    private val highPassCutoffHz: Double = 120.0
) {
    // 2nd-order Butterworth Biquad High-Pass Filter state
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    // Dynamics Compressor envelope state
    private var envelope = 0.0
    private val attackCoeff = exp(-1.0 / (0.006 * sampleRateHz))   // 6 ms attack
    private val releaseCoeff = exp(-1.0 / (0.090 * sampleRateHz))  // 90 ms release
    private val thresholdDb = -24.0
    private val ratio = 4.0
    private val makeupGainLinear = 10.0.pow(6.0 / 20.0)            // +6 dB speech clarity boost

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var aec: AcousticEchoCanceler? = null
    private var captureJob: Job? = null
    private val recordedPcm = ByteArrayOutputStream()

    init {
        val omega = 2.0 * PI * highPassCutoffHz / sampleRateHz.toDouble()
        val cosW = cos(omega)
        val alpha = sin(omega) / sqrt(2.0) // Q = 0.7071 (Butterworth)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW) / 2.0) / a0
        b1 = (-(1.0 + cosW)) / a0
        b2 = ((1.0 + cosW) / 2.0) / a0
        a1 = (-2.0 * cosW) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun resetFilterState() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
        envelope = 0.0
    }

    /**
     * Applies 120Hz Biquad High-Pass + Dynamics Compression in-place to a 16-bit PCM frame.
     * Returns (denoisedRms0to1, estimatedRumbleCutDb).
     */
    fun processPcmFrameInPlace(pcm: ShortArray, count: Int, enableDsp: Boolean = true): Pair<Float, Float> {
        if (count <= 0) return 0f to 0f
        var rawSq = 0.0
        var outSq = 0.0

        for (i in 0 until count) {
            val rawSample = pcm[i].toDouble() / 32768.0
            rawSq += rawSample * rawSample

            val processed = if (enableDsp) {
                // Stage 1: 120Hz High-Pass Biquad (removes low-frequency fan/wind rumble)
                val hp = b0 * rawSample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = rawSample
                y2 = y1
                y1 = hp

                // Stage 2: Dynamics Compressor (normalizes quiet speech, tames peaks)
                val level = abs(hp)
                val coeff = if (level > envelope) attackCoeff else releaseCoeff
                envelope = coeff * envelope + (1.0 - coeff) * level

                val envDb = 20.0 * log10(envelope.coerceAtLeast(1e-6))
                val gainReductionDb = if (envDb > thresholdDb) {
                    (thresholdDb - envDb) * (1.0 - 1.0 / ratio)
                } else {
                    0.0
                }
                val gain = 10.0.pow(gainReductionDb / 20.0) * makeupGainLinear
                (hp * gain).coerceIn(-0.98, 0.98)
            } else {
                rawSample
            }

            outSq += processed * processed
            pcm[i] = (processed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }

        val rawRms = sqrt(rawSq / count).coerceAtLeast(1e-6)
        val outRms = sqrt(outSq / count).coerceAtLeast(1e-6)
        val normalizedLevel = ((outRms * 5.2).coerceIn(0.0, 1.0)).toFloat()
        val rumbleDeltaDb = if (enableDsp) {
            (20.0 * log10(rawRms / outRms)).coerceIn(0.0, 18.0).toFloat()
        } else {
            0f
        }
        return normalizedLevel to rumbleDeltaDb
    }

    @SuppressLint("MissingPermission")
    fun startCapture(
        scope: CoroutineScope,
        enableDsp: Boolean = true,
        recordToBuffer: Boolean = true,
        onTelemetry: (VoiceDspTelemetry) -> Unit
    ): Boolean {
        stopCapture()
        resetFilterState()
        if (recordToBuffer) recordedPcm.reset()

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull() ?: return false

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            return false
        }

        val sessionId = recorder.audioSessionId
        var nsAttached = false
        var agcAttached = false
        if (enableDsp) {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching {
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
                nsAttached = noiseSuppressor != null
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = runCatching {
                    AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
                agcAttached = agc != null
            }
            if (AcousticEchoCanceler.isAvailable()) {
                aec = runCatching {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }
        }

        audioRecord = recorder
        val started = runCatching {
            recorder.startRecording()
            true
        }.getOrDefault(false)
        if (!started) {
            stopCapture()
            return false
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val frame = ShortArray(512)
            val byteFrame = ByteArray(frame.size * 2)
            while (isActive) {
                val read = runCatching { recorder.read(frame, 0, frame.size) }.getOrDefault(-1)
                if (read > 0) {
                    val (level, cutDb) = processPcmFrameInPlace(frame, read, enableDsp)
                    if (recordToBuffer && recordedPcm.size() < 4_800_000) {
                        for (i in 0 until read) {
                            val s = frame[i].toInt()
                            byteFrame[i * 2] = (s and 0xFF).toByte()
                            byteFrame[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                        }
                        recordedPcm.write(byteFrame, 0, read * 2)
                    }
                    onTelemetry(
                        VoiceDspTelemetry(
                            isActive = true,
                            levelNormalized = level,
                            hardwareNsAttached = nsAttached,
                            hardwareAgcAttached = agcAttached,
                            highPassCutoffHz = highPassCutoffHz.toInt(),
                            noiseReductionDb = cutDb
                        )
                    )
                } else {
                    break
                }
            }
        }
        return true
    }

    fun stopCapture(saveWavTo: File? = null): File? {
        captureJob?.cancel()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { agc?.release() }
        agc = null
        runCatching { aec?.release() }
        aec = null

        val pcmBytes = recordedPcm.toByteArray()
        if (saveWavTo != null && pcmBytes.isNotEmpty()) {
            return runCatching {
                writePcmAsWav(pcmBytes, saveWavTo)
                saveWavTo
            }.getOrNull()
        }
        return null
    }

    private fun writePcmAsWav(pcmBytes: ByteArray, dest: File) {
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRateHz.toLong() * 2L
        FileOutputStream(dest).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0 // PCM
            header[22] = 1; header[23] = 0 // Mono
            header[24] = (sampleRateHz and 0xff).toByte()
            header[25] = ((sampleRateHz shr 8) and 0xff).toByte()
            header[26] = ((sampleRateHz shr 16) and 0xff).toByte()
            header[27] = ((sampleRateHz shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2; header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            out.write(header)
            out.write(pcmBytes)
        }
    }
}
