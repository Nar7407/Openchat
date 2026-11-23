package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.domain.model.AmbientNoiseLevel
import com.example.domain.model.SmartContextSnapshot
import com.example.domain.model.SmartZone
import com.example.domain.model.TimeProfileMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Evaluates real device context (clock time profile, GPS geofence zones, battery %,
 * cellular/metered network speed, and ambient microphone dB) for Smart Triggers.
 */
object SmartContextEngine {

    fun evaluateTimeProfile(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): TimeProfileMode {
        return when {
            hourOfDay in 9..17 -> TimeProfileMode.STUDY_WORK
            hourOfDay >= 22 || hourOfDay < 6 -> TimeProfileMode.NIGHT
            else -> TimeProfileMode.DAY
        }
    }

    fun readBatteryStatus(context: Context): Pair<Int, Boolean> {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return 100 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    fun isSlowOrMeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val isCellularOnly = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val downstreamKbps = caps.linkDownstreamBandwidthKbps
        return (isCellularOnly && downstreamKbps in 1..2500) ||
            (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && downstreamKbps in 1..1500)
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOfNotNull(
            if (hasFine) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best
    }

    fun matchGeofenceZone(location: Location?, zones: List<SmartZone>): SmartZone? {
        if (location == null || zones.isEmpty()) return null
        val result = FloatArray(1)
        var closest: Pair<SmartZone, Float>? = null
        for (zone in zones) {
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                zone.latitude,
                zone.longitude,
                result
            )
            val dist = result[0]
            if (dist <= zone.radiusMeters.coerceAtLeast(50f)) {
                if (closest == null || dist < closest.second) {
                    closest = zone to dist
                }
            }
        }
        return closest?.first
    }

    fun captureSnapshot(
        context: Context,
        savedZones: List<SmartZone>,
        timeProfilesEnabled: Boolean,
        autoEcoEnabled: Boolean,
        ambientDb: Int? = null
    ): SmartContextSnapshot {
        val timeProfile = if (timeProfilesEnabled) evaluateTimeProfile() else TimeProfileMode.DAY
        val (batteryPct, charging) = readBatteryStatus(context)
        val slowNet = isSlowOrMeteredNetwork(context)
        val eco = autoEcoEnabled && ((batteryPct < 20 && !charging) || slowNet)
        val matchedZone = matchGeofenceZone(getLastKnownLocation(context), savedZones)
        val noiseLevel = when {
            ambientDb == null -> AmbientNoiseLevel.NORMAL
            ambientDb < 42 -> AmbientNoiseLevel.QUIET_WHISPER
            ambientDb > 68 -> AmbientNoiseLevel.NOISY_BOOST
            else -> AmbientNoiseLevel.NORMAL
        }
        return SmartContextSnapshot(
            timeProfile = timeProfile,
            matchedZone = matchedZone,
            batteryPercent = batteryPct,
            isCharging = charging,
            isSlowNetwork = slowNet,
            isEcoModeActive = eco,
            ambientDb = ambientDb,
            noiseLevel = noiseLevel
        )
    }

    /**
     * Samples ~350ms of live microphone audio via AudioRecord to estimate ambient SPL (dB).
     * Returns null if RECORD_AUDIO permission is not granted or hardware is busy.
     */
    @SuppressLint("MissingPermission")
    suspend fun sampleAmbientDecibels(context: Context): Int? = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext null

        val sampleRate = 8000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return@withContext null

        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext null
            val buffer = ShortArray(minBuf)
            recorder.startRecording()
            var sumSq = 0.0
            var totalSamples = 0
            repeat(3) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        val v = buffer[i].toDouble()
                        sumSq += v * v
                    }
                    totalSamples += read
                }
            }
            recorder.stop()
            if (totalSamples == 0) return@withContext null
            val rms = sqrt(sumSq / totalSamples).coerceAtLeast(1.0)
            // Calibrate 16-bit PCM RMS (1..32767) into approximate room SPL (~25 dB to 95 dB)
            val spl = (20.0 * log10(rms) + 5.0).toInt().coerceIn(22, 100)
            spl
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { recorder?.release() }
        }
    }
}
