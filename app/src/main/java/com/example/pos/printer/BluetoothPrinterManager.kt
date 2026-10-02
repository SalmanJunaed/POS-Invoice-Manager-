package com.example.pos.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Robust Bluetooth Hardware Manager for ESC/POS Thermal Printers.
 * Handles device discovery, bonded devices, RFCOMM connection fallbacks,
 * socket timeout guarantees, thread-safe transmission, and disconnection recovery.
 */
object BluetoothPrinterManager {

    private const val TAG = "BluetoothPrinterMgr"

    // Standard Bluetooth Serial Port Profile (SPP) UUID
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Connection timeout in milliseconds
    private const val CONNECT_TIMEOUT_MS = 6000L

    private val ioMutex = Mutex()

    // Active connection references
    private var activeSocket: BluetoothSocket? = null
    private var activeOutputStream: OutputStream? = null
    private var connectedAddress: String? = null
    private var connectedName: String? = null

    // Connection State
    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    // Paired & Discovered Devices
    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceItem>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Discovery Broadcast Receiver
    private var discoveryReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    val bluetoothAdapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Checks if all necessary Bluetooth runtime permissions are granted.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Refreshes the list of already bonded / paired Bluetooth devices.
     */
    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(context: Context? = null) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _pairedDevices.value = emptyList()
            return
        }
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            _pairedDevices.value = bonded.map { device ->
                mapToDeviceItem(device, isBonded = true)
            }.sortedWith(compareByDescending<BluetoothDeviceItem> { it.isPrinter }.thenBy { it.name })
        } catch (e: Exception) {
            Log.e(TAG, "Error querying bonded devices", e)
            _pairedDevices.value = emptyList()
        }
    }

    /**
     * Starts searching for nearby Bluetooth devices (Discovery).
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _connectionState.value = PrinterConnectionState.Error("Bluetooth is turned OFF. Please enable Bluetooth.")
            return
        }

        refreshPairedDevices(context)
        _discoveredDevices.value = emptyList()

        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }

        // Register broadcast receiver for discovery events
        registerReceiver(context)

        _isScanning.value = true
        val started = adapter.startDiscovery()
        if (!started) {
            _isScanning.value = false
            Log.w(TAG, "Bluetooth startDiscovery returned false")
        }
    }

    /**
     * Stops ongoing Bluetooth discovery and unregisters receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery", e)
        }
        _isScanning.value = false
        unregisterReceiver(context)
    }

    private fun registerReceiver(context: Context) {
        if (isReceiverRegistered) return
        discoveryReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null && !device.address.isNullOrBlank()) {
                            val item = mapToDeviceItem(device, isBonded = device.bondState == BluetoothDevice.BOND_BONDED)
                            // Append if not already present
                            val current = _discoveredDevices.value.toMutableList()
                            if (current.none { it.address == item.address }) {
                                current.add(item)
                                _discoveredDevices.value = current.sortedWith(
                                    compareByDescending<BluetoothDeviceItem> { it.isPrinter }.thenBy { it.name }
                                )
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            context.registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register discovery receiver", e)
        }
    }

    private fun unregisterReceiver(context: Context) {
        if (!isReceiverRegistered || discoveryReceiver == null) return
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        } finally {
            isReceiverRegistered = false
            discoveryReceiver = null
        }
    }

    /**
     * Connects to a Bluetooth printer using multiple fallback socket methods.
     * Guarantees socket timeout without hanging the UI.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(
        deviceAddress: String,
        deviceName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val adapter = bluetoothAdapter
                ?: return@withContext Result.failure(Exception("Bluetooth hardware not available on this device"))

            if (!adapter.isEnabled) {
                val err = "Bluetooth is currently turned OFF. Please turn on Bluetooth."
                _connectionState.value = PrinterConnectionState.Error(err, deviceName)
                return@withContext Result.failure(Exception(err))
            }

            val displayName = deviceName ?: getPairedDeviceName(deviceAddress) ?: "Thermal Printer"
            _connectionState.value = PrinterConnectionState.Connecting(displayName, deviceAddress)

            // Cancel discovery to free RFCOMM bandwidth
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: Exception) {}

            // Close existing socket if connecting to different device or closed
            if (activeSocket != null) {
                closeSocketSilently()
            }

            var socket: BluetoothSocket? = null
            var connected = false

            val connectAttempt = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                try {
                    val device = adapter.getRemoteDevice(deviceAddress)

                    // Method 1: Standard SPP RFCOMM socket
                    try {
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        socket?.connect()
                        connected = true
                    } catch (e1: Exception) {
                        Log.w(TAG, "Method 1 standard SPP connect failed: ${e1.message}. Trying insecure RFCOMM...")
                        closeSocketSilently(socket)
                        socket = null
                    }

                    // Method 2: Insecure RFCOMM socket (some thermal printers don't support secure auth)
                    if (!connected) {
                        try {
                            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                            socket?.connect()
                            connected = true
                        } catch (e2: Exception) {
                            Log.w(TAG, "Method 2 insecure connect failed: ${e2.message}. Trying reflection channel 1...")
                            closeSocketSilently(socket)
                            socket = null
                        }
                    }

                    // Method 3: Reflection on channel 1 (classic ESC/POS hardware workaround)
                    if (!connected) {
                        try {
                            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            @Suppress("UNCHECKED_CAST")
                            socket = m.invoke(device, 1) as? BluetoothSocket
                            socket?.connect()
                            connected = true
                        } catch (e3: Exception) {
                            Log.e(TAG, "Method 3 channel 1 connect failed: ${e3.message}")
                            closeSocketSilently(socket)
                            socket = null
                        }
                    }

                    connected
                } catch (e: Exception) {
                    Log.e(TAG, "General connection failure", e)
                    false
                }
            }

            if (connectAttempt == true && socket != null && socket?.isConnected == true) {
                activeSocket = socket
                activeOutputStream = socket?.outputStream
                connectedAddress = deviceAddress
                connectedName = displayName
                _connectionState.value = PrinterConnectionState.Connected(displayName, deviceAddress)
                Result.success(Unit)
            } else {
                closeSocketSilently(socket)
                val errMsg = "Failed to connect to printer $displayName ($deviceAddress). Please ensure the printer is turned ON, within range, and paired."
                _connectionState.value = PrinterConnectionState.Error(errMsg, displayName)
                Result.failure(Exception(errMsg))
            }
        }
    }

    /**
     * Disconnects the active Bluetooth printer connection.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeSocketSilently()
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    /**
     * Prints a byte payload to the specified device.
     * Automatically connects if needed, or re-uses existing open connection.
     */
    suspend fun print(
        deviceAddress: String,
        deviceName: String?,
        payload: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val displayName = deviceName ?: connectedName ?: getPairedDeviceName(deviceAddress) ?: "Printer"

            // Check if existing socket is already connected to this address
            val needConnect = activeSocket == null || !activeSocket!!.isConnected || connectedAddress != deviceAddress

            if (needConnect) {
                val connectRes = connectInternal(deviceAddress, displayName)
                if (connectRes.isFailure) {
                    return@withContext connectRes
                }
            }

            _connectionState.value = PrinterConnectionState.Printing(displayName, "Sending receipt bytes...")

            try {
                val stream = activeOutputStream ?: throw IOException("Output stream is null")
                // Write payload in chunks to avoid overwhelming thermal buffer
                val chunkSize = 512
                var offset = 0
                while (offset < payload.size) {
                    val length = minOf(chunkSize, payload.size - offset)
                    stream.write(payload, offset, length)
                    stream.flush()
                    offset += length
                }

                _connectionState.value = PrinterConnectionState.Connected(displayName, deviceAddress)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Transmission failed, attempting socket cleanup", e)
                closeSocketSilently()
                val errMsg = "Printer communication failed: ${e.message ?: "Disconnected during printing"}"
                _connectionState.value = PrinterConnectionState.Error(errMsg, displayName)
                Result.failure(Exception(errMsg))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal(deviceAddress: String, displayName: String): Result<Unit> {
        val adapter = bluetoothAdapter ?: return Result.failure(Exception("Bluetooth hardware unavailable"))
        if (!adapter.isEnabled) return Result.failure(Exception("Bluetooth is turned OFF"))

        closeSocketSilently()

        val device = adapter.getRemoteDevice(deviceAddress)
        var socket: BluetoothSocket? = null

        // Try standard SPP
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            socket.connect()
        } catch (e1: Exception) {
            closeSocketSilently(socket)
            // Try insecure
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e2: Exception) {
                closeSocketSilently(socket)
                // Try channel 1
                try {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    @Suppress("UNCHECKED_CAST")
                    socket = m.invoke(device, 1) as? BluetoothSocket
                    socket?.connect()
                } catch (e3: Exception) {
                    closeSocketSilently(socket)
                    return Result.failure(Exception("Could not open Bluetooth communication channel: ${e3.message}"))
                }
            }
        }

        if (socket != null && socket.isConnected) {
            activeSocket = socket
            activeOutputStream = socket.outputStream
            connectedAddress = deviceAddress
            connectedName = displayName
            _connectionState.value = PrinterConnectionState.Connected(displayName, deviceAddress)
            return Result.success(Unit)
        }
        return Result.failure(Exception("Printer unreachable"))
    }

    private fun closeSocketSilently(socket: BluetoothSocket? = activeSocket) {
        try {
            activeOutputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        if (socket == activeSocket) {
            activeOutputStream = null
            activeSocket = null
            connectedAddress = null
            connectedName = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDeviceName(address: String): String? {
        return try {
            bluetoothAdapter?.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }?.name
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapToDeviceItem(device: BluetoothDevice, isBonded: Boolean): BluetoothDeviceItem {
        val name = try { device.name?.takeIf { it.isNotBlank() } ?: "Thermal Printer" } catch (_: Exception) { "Thermal Printer" }
        val address = device.address ?: "00:00:00:00:00:00"
        val devClass = try { device.bluetoothClass?.majorDeviceClass ?: 0 } catch (_: Exception) { 0 }
        val isPrinter = devClass == BluetoothClass.Device.Major.IMAGING ||
                name.contains("print", ignoreCase = true) ||
                name.contains("pos", ignoreCase = true) ||
                name.contains("thermal", ignoreCase = true) ||
                name.contains("rpp", ignoreCase = true) ||
                name.contains("mpt", ignoreCase = true)
        return BluetoothDeviceItem(
            name = name,
            address = address,
            isBonded = isBonded,
            isPrinter = isPrinter
        )
    }
}

/**
 * Representation of a Bluetooth device.
 */
data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean = false,
    val isPrinter: Boolean = false
)

/**
 * UI & Connection state for the Bluetooth printer hardware module.
 */
sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    data class Connecting(val deviceName: String, val address: String) : PrinterConnectionState()
    data class Connected(val deviceName: String, val address: String) : PrinterConnectionState()
    data class Printing(val deviceName: String, val step: String) : PrinterConnectionState()
    data class Error(val message: String, val deviceName: String? = null, val canRetry: Boolean = true) : PrinterConnectionState()
}
