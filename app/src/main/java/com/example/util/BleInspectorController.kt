package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BleScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)

data class BleTelemetryLine(
    val timestamp: String,
    val characteristicUuid: String,
    val hexPayload: String,
    val utf8Preview: String
)

/**
 * Real Android BLE Scanner & GATT Notification Inspector for ESP32, Arduino, smartwatches,
 * and custom BLE sensor projects. Streams live characteristic values ready to send to AI Chat.
 */
class BleInspectorController(private val context: Context) {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<BleScannedDevice>>(emptyList())
    val devices: StateFlow<List<BleScannedDevice>> = _devices.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _statusMessage = MutableStateFlow("BLE // Ready to scan nearby peripherals.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _telemetryLogs = MutableStateFlow<List<BleTelemetryLine>>(emptyList())
    val telemetryLogs: StateFlow<List<BleTelemetryLine>> = _telemetryLogs.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: result.scanRecord?.deviceName
                ?: "BLE Device"
            val entry = BleScannedDevice(name = name, address = dev.address, rssi = result.rssi, device = dev)
            val current = _devices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == entry.address }
            if (idx >= 0) current[idx] = entry else current.add(entry)
            _devices.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _statusMessage.value = "BLE // Scan failed (code $errorCode)."
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            _statusMessage.value = "BLE // Grant Bluetooth permissions first."
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            _statusMessage.value = "BLE // Enable Bluetooth on your phone to scan."
            return
        }
        val scanner = bt.bluetoothLeScanner
        if (scanner == null) {
            _statusMessage.value = "BLE // Bluetooth LE scanner unavailable."
            return
        }
        _devices.value = emptyList()
        _isScanning.value = true
        _statusMessage.value = "BLE // Scanning for ESP32 / Arduino / BLE sensors…"
        runCatching { scanner.startScan(scanCallback) }.onFailure {
            _isScanning.value = false
            _statusMessage.value = "BLE // ${it.message ?: "Scan error"}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _isScanning.value = false
        _statusMessage.value = "BLE // Scan stopped (${_devices.value.size} found)."
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceEntry: BleScannedDevice) {
        stopScan()
        disconnect()
        _statusMessage.value = "BLE // Connecting to ${deviceEntry.name}…"
        gatt = deviceEntry.device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectedName.value = "${deviceEntry.name} (${deviceEntry.address})"
                    _statusMessage.value = "BLE // Connected — discovering GATT services…"
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectedName.value = null
                    _statusMessage.value = "BLE // Disconnected from ${deviceEntry.name}."
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                var subscribed = 0
                val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                for (svc in g.services) {
                    for (ch in svc.characteristics) {
                        val props = ch.properties
                        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        ) {
                            g.setCharacteristicNotification(ch, true)
                            val desc = ch.getDescriptor(cccdUuid)
                            if (desc != null) {
                                @Suppress("DEPRECATION")
                                run {
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    g.writeDescriptor(desc)
                                }
                            }
                            subscribed++
                        } else if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            g.readCharacteristic(ch)
                        }
                    }
                }
                _statusMessage.value = "BLE // ${g.services.size} services found · Subscribed to $subscribed telemetry streams."
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                recordPayload(characteristic.uuid.toString(), characteristic.value)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                recordPayload(characteristic.uuid.toString(), value)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                recordPayload(characteristic.uuid.toString(), characteristic.value)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                recordPayload(characteristic.uuid.toString(), value)
            }
        })
    }

    private fun recordPayload(uuid: String, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        val hex = bytes.joinToString(" ") { String.format(Locale.US, "%02X", it) }
        val utf8 = String(bytes, Charsets.UTF_8).replace(Regex("""[^\x20-\x7E]"""), ".")
        val line = BleTelemetryLine(
            timestamp = timeFmt.format(Date()),
            characteristicUuid = uuid.take(8),
            hexPayload = hex,
            utf8Preview = utf8
        )
        _telemetryLogs.value = (listOf(line) + _telemetryLogs.value).take(60)
    }

    fun formatTelemetryForAi(): String = buildString {
        appendLine("Analyze this live Bluetooth LE (BLE) sensor telemetry captured from ${_connectedName.value ?: "my BLE device"}:")
        appendLine("```")
        _telemetryLogs.value.take(25).forEach { log ->
            appendLine("[${log.timestamp}] UUID=${log.characteristicUuid} HEX=[${log.hexPayload}] ASCII=\"${log.utf8Preview}\"")
        }
        appendLine("```")
        append("Decode likely sensor values, anomalies, and protocol structure.")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _connectedName.value = null
    }

    fun release() {
        stopScan()
        disconnect()
    }
}
