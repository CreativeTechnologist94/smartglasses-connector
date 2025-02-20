
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

Now let's update the UDPTransceiver to extend the BaseTransceiver:

<lov-write file_path="src/networking/UDPTransceiver.kt">
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
) : BaseTransceiver() {
    private var receiverSocket: DatagramSocket? = null
    private var senderSocket: DatagramSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val messageQueue = ConcurrentLinkedQueue<Pair<String, String>>() // message, ipAddress

    fun start() {
        isRunning = true
        initialize() // Call base class initialization
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
                    // First route the message through base transceiver
                    routeMessageToDevice(message, address)
                    // Then call the callback
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
