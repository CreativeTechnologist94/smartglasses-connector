
```kotlin
package com.google.xrinputwearos.networking

import android.util.Log
import com.google.xrinputwearos.device.Device
import java.util.concurrent.ConcurrentHashMap

open class BaseTransceiver {
    protected val devicesByAddress = ConcurrentHashMap<String, Device>()
    protected val devicesByID = ConcurrentHashMap<Int, Device>()
    protected val devices = mutableListOf<Device>()
    protected val registeredAddresses = mutableListOf<String>()

    fun initialize() {
        // Clear existing collections
        devicesByAddress.clear()
        devicesByID.clear()
        devices.clear()
        registeredAddresses.clear()
        
        Log.d(TAG, "Transceiver initialized")
    }

    protected fun routeMessageToDevice(message: String, address: String) {
        if (!registeredAddresses.contains(address)) {
            Log.d(TAG, "Address not registered: $address")
            return
        }
        
        devicesByAddress[address]?.parseData(message)
    }

    fun getDevices(): List<Device> = devices.toList()

    fun getIDs(): List<Int> = devicesByID.keys.toList()

    fun getDevicesByID(): Map<Int, Device> = devicesByID.toMap()

    fun getDevicesByAddress(): Map<String, Device> = devicesByAddress.toMap()

    companion object {
        private const val TAG = "BaseTransceiver"
    }
}
```

Next, let's create the socket manager:

<lov-write file_path="src/networking/UDPSocketManager.kt">
```kotlin
package com.google.xrinputwearos.networking

import android.util.Log
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class UDPSocketManager(
    private val listenerPort: Int,
    private val senderPort: Int,
    private val receiveBufferSize: Int
) {
    private var receiverSocket: DatagramSocket? = null
    private var senderSocket: DatagramSocket? = null

    fun initializeReceiver(): Boolean {
        return try {
            receiverSocket = DatagramSocket(listenerPort).apply {
                reuseAddress = true
                broadcast = true
                receiveBufferSize = this@UDPSocketManager.receiveBufferSize
            }
            Log.d(TAG, "UDP Receiver initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize receiver", e)
            false
        }
    }

    fun initializeSender(): Boolean {
        return try {
            senderSocket = DatagramSocket()
            Log.d(TAG, "UDP Sender initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sender", e)
            false
        }
    }

    fun receivePacket(buffer: ByteArray): Pair<String, String>? {
        return try {
            val packet = DatagramPacket(buffer, buffer.size)
            receiverSocket?.receive(packet)
            val message = String(
                packet.data,
                packet.offset,
                packet.length,
                StandardCharsets.UTF_8
            )
            val senderAddress = packet.address.hostAddress
            message to senderAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving UDP packet", e)
            null
        }
    }

    fun sendPacket(message: String, ipAddress: String): Boolean {
        return try {
            val data = message.toByteArray(StandardCharsets.UTF_8)
            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(data, data.size, address, senderPort)
            senderSocket?.send(packet)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending UDP packet", e)
            false
        }
    }

    fun close() {
        receiverSocket?.close()
        senderSocket?.close()
        receiverSocket = null
        senderSocket = null
    }

    companion object {
        private const val TAG = "UDPSocketManager"
    }
}
```

Now the message processor:

<lov-write file_path="src/networking/MessageProcessor.kt">
```kotlin
package com.google.xrinputwearos.networking

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class MessageProcessor(
    private val onMessageReceived: (String, String) -> Unit,
    private val onHeartbeat: (String) -> Unit
) {
    private val messageQueue = ConcurrentLinkedQueue<Pair<String, String>>()
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun start() {
        isRunning = true
        startProcessing()
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    fun addMessage(message: String, address: String) {
        messageQueue.offer(message to address)
    }

    private fun startProcessing() {
        scope.launch {
            while (isRunning) {
                messageQueue.poll()?.let { (message, address) ->
                    onMessageReceived(message, address)
                    onHeartbeat(address)
                }
                delay(10)
            }
        }
    }

    companion object {
        private const val TAG = "MessageProcessor"
    }
}
```

The UDP transceiver implementation:

<lov-write file_path="src/networking/UDPTransceiver.kt">
```kotlin
package com.google.xrinputwearos.networking

import android.util.Log
import kotlinx.coroutines.*

class UDPTransceiver(
    private val listenerPort: Int = 5555,
    private val senderPort: Int = 5556,
    private val receiveBufferSize: Int = 120000,
    private val onMessageReceived: (String, String) -> Unit
) : BaseTransceiver() {
    private val socketManager = UDPSocketManager(listenerPort, senderPort, receiveBufferSize)
    private val messageProcessor = MessageProcessor(
        onMessageReceived = { message, address ->
            routeMessageToDevice(message, address)
            onMessageReceived(message, address)
        },
        onHeartbeat = { address ->
            sendMessage("HEARTBEAT", address)
        }
    )
    
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun start() {
        isRunning = true
        initialize()
        if (socketManager.initializeReceiver() && socketManager.initializeSender()) {
            messageProcessor.start()
            startListening()
        }
    }

    private fun startListening() {
        scope.launch {
            val buffer = ByteArray(receiveBufferSize)
            while (isRunning) {
                socketManager.receivePacket(buffer)?.let { (message, address) ->
                    messageProcessor.addMessage(message, address)
                }
            }
        }
    }

    fun sendMessage(message: String, ipAddress: String) {
        scope.launch {
            socketManager.sendPacket(message, ipAddress)
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        messageProcessor.stop()
        socketManager.close()
    }

    companion object {
        private const val TAG = "UDPTransceiver"
    }
}
```

The device-related classes:

<lov-write file_path="src/device/Device.kt">
```kotlin
package com.google.xrinputwearos.device

import android.util.Log

class Device(
    var id: Int = -1,
    var address: String = "",
    var deviceInfo: DeviceInfo? = null,
    var sensorData: SensorData = SensorData()
) {
    private var receivedDeviceInfo = false

    fun parseData(message: String) {
        try {
            val parts = message.split(",")
            if (parts.size < 2) return
            
            val timestamp = parts[0].toLong()
            val header = parts[1]
            val data = parts.drop(2)

            when (header) {
                "DEVICE_INFO" -> handleDeviceInfo(data)
                // Add other message handlers here for sensors, touch events, etc.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    private fun handleDeviceInfo(data: List<String>) {
        if (data.size < 5) return
        
        receivedDeviceInfo = true
        deviceInfo = DeviceInfo(
            id = id,
            address = address,
            deviceName = data[0],
            sizePx = Vector2(data[1].toFloat(), data[2].toFloat()),
            sizeM = Vector2(data[3].toFloat() * 0.0254f, data[4].toFloat() * 0.0254f)
        )
    }

    companion object {
        private const val TAG = "Device"
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

And finally, the communication manager:

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
