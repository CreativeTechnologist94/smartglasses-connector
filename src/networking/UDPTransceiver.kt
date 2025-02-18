
```kotlin
package com.google.xrinputwearos.networking

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

class UDPTransceiver(
    private val listenerPort: Int = 5555,
    private val senderPort: Int = 5556,
    private val receiveBufferSize: Int = 120000,
    private val onMessageReceived: (String, String) -> Unit
) {
    private var receiverSocket: DatagramSocket? = null
    private var senderSocket: DatagramSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val messageQueue = ConcurrentLinkedQueue<Pair<String, String>>() // message, ipAddress

    fun start() {
        isRunning = true
        initializeReceiver()
        initializeSender()
        startListening()
        startMessageProcessor()
    }

    private fun initializeReceiver() {
        try {
            receiverSocket = DatagramSocket(listenerPort).apply {
                reuseAddress = true
                broadcast = true
                receiveBufferSize = this@UDPTransceiver.receiveBufferSize
            }
            Log.d(TAG, "UDP Receiver initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize receiver", e)
        }
    }

    private fun initializeSender() {
        try {
            senderSocket = DatagramSocket()
            Log.d(TAG, "UDP Sender initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sender", e)
        }
    }

    private fun startListening() {
        scope.launch {
            val buffer = ByteArray(receiveBufferSize)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    receiverSocket?.receive(packet)
                    val message = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        StandardCharsets.UTF_8
                    )
                    val senderAddress = packet.address.hostAddress
                    messageQueue.offer(message to senderAddress)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error receiving UDP packet", e)
                    }
                }
            }
        }
    }

    private fun startMessageProcessor() {
        scope.launch {
            while (isRunning) {
                messageQueue.poll()?.let { (message, address) ->
                    onMessageReceived(message, address)
                    // Send heartbeat back
                    sendMessage("HEARTBEAT", address)
                }
                delay(10) // Small delay to prevent busy waiting
            }
        }
    }

    fun sendMessage(message: String, ipAddress: String) {
        scope.launch {
            try {
                val data = message.toByteArray(StandardCharsets.UTF_8)
                val address = InetAddress.getByName(ipAddress)
                val packet = DatagramPacket(data, data.size, address, senderPort)
                senderSocket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending UDP packet", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        receiverSocket?.close()
        senderSocket?.close()
        receiverSocket = null
        senderSocket = null
    }

    companion object {
        private const val TAG = "UDPTransceiver"
    }
}
```

<lov-write file_path="src/device/DeviceManager.kt">
```kotlin
package com.google.xrinputwearos.device

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceManager {
    private val _connectedDevices = MutableStateFlow<Map<String, DeviceInfo>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, DeviceInfo>> = _connectedDevices.asStateFlow()
    private var nextId = 0

    fun handleNewDevice(address: String, deviceInfo: DeviceInfo) {
        if (!_connectedDevices.value.containsKey(address)) {
            val newId = nextId++
            val updatedDevices = _connectedDevices.value.toMutableMap()
            updatedDevices[address] = deviceInfo.copy(id = newId)
            _connectedDevices.value = updatedDevices
            Log.d(TAG, "Added new device: $address with ID: $newId")
        }
    }

    fun removeDevice(address: String) {
        val updatedDevices = _connectedDevices.value.toMutableMap()
        updatedDevices.remove(address)
        _connectedDevices.value = updatedDevices
        Log.d(TAG, "Removed device: $address")
    }

    companion object {
        private const val TAG = "DeviceManager"
    }
}
```

<lov-write file_path="src/device/DeviceInfo.kt">
```kotlin
package com.google.xrinputwearos.device

data class DeviceInfo(
    val id: Int = -1,
    val address: String,
    val deviceName: String,
    val sizePx: Vector2,
    val sizeM: Vector2,
    val orientation: DeviceOrientation = DeviceOrientation.PORTRAIT
)

data class Vector2(
    val x: Float,
    val y: Float
)

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
)

data class SensorData(
    val accelerometer: Vector3 = Vector3(0f, 0f, 0f),
    val gravity: Vector3 = Vector3(0f, 0f, 0f),
    val gyroscope: Vector3 = Vector3(0f, 0f, 0f),
    val magneticField: Vector3 = Vector3(0f, 0f, 0f),
    val proximity: Float = 0f,
    val light: Float = 0f,
    val temperature: Float = 0f
)

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    LANDSCAPE_RIGHT,
    PORTRAIT_UPSIDE_DOWN,
    FACE_UP,
    FACE_DOWN
}
```

<lov-write file_path="src/communication/CommunicationManager.kt">
```kotlin
package com.google.xrinputwearos.communication

import android.util.Log
import com.google.xrinputwearos.device.DeviceManager
import com.google.xrinputwearos.networking.UDPTransceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommunicationManager(
    private val deviceManager: DeviceManager
) {
    private var udpTransceiver: UDPTransceiver? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        udpTransceiver = UDPTransceiver(
            onMessageReceived = { message, address ->
                handleMessage(message, address)
            }
        ).also {
            it.start()
        }
    }

    private fun handleMessage(message: String, address: String) {
        scope.launch {
            try {
                val parts = message.split(",")
                if (parts.size < 2) return@launch
                
                val timestamp = parts[0].toLong()
                val header = parts[1]
                val data = parts.drop(2)

                when (header) {
                    "WHOAREYOU" -> sendDeviceInfo(address)
                    "DEVICE_INFO" -> handleDeviceInfo(address, data)
                    // Add other message handlers here
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message", e)
            }
        }
    }

    private fun handleDeviceInfo(address: String, data: List<String>) {
        // Implementation for handling device info messages
        // This would parse the device info and add it to the DeviceManager
    }

    private fun sendDeviceInfo(address: String) {
        // Implementation for sending device info
        // This would gather device info and send it via UDP
    }

    fun stop() {
        udpTransceiver?.stop()
        udpTransceiver = null
    }

    companion object {
        private const val TAG = "CommunicationManager"
    }
}
```
