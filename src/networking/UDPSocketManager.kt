
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

2. Now let's create a message processor for handling the message queue:

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

3. Finally, let's update the UDPTransceiver to use these new components:

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
