package com.steve1316.uma_android_automation

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

internal fun interface HostSocketConnector {
    fun connect(port: Int): Socket
}

internal class HostAdbInputTransport(
    private val configuration: HostInputConfiguration,
    private val appVersion: String,
    private val devicePort: Int = HOST_DEVICE_PORT,
    private val connector: HostSocketConnector =
        HostSocketConnector { port ->
            Socket().apply {
                connect(InetSocketAddress("127.0.0.1", port), 1500)
                soTimeout = 3500
                tcpNoDelay = true
            }
        },
) : InputTransport {
    override val authority: InputTransportAuthority = InputTransportAuthority.HOST_ADB

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var session: HostProtocolSession? = null

    @Synchronized
    override fun health(): InputHealth {
        return try {
            val activeSession = ensureSession()
            val request = HostAdbProtocol.buildHealthRequest(activeSession, HostAdbProtocol.newToken(18))
            writeMessage(request)
            val result = readMessage()
            HostAdbProtocol.acceptResult(activeSession, request, result, "HEALTH_RESULT").first
        } catch (_: Exception) {
            close()
            InputHealth(InputExecutionStatus.UNAVAILABLE, foreground = false, capabilities = emptySet(), targetId = configuration.pairing.targetId)
        }
    }

    @Synchronized
    override fun swipe(request: BoundedSwipe): InputExecutionResult {
        request.validationError()?.let {
            return InputExecutionResult(InputExecutionStatus.REJECTED, foreground = false, detailCode = it)
        }
        var dispatchMayHaveOccurred = false
        return try {
            val activeSession = ensureSession()
            val message = HostAdbProtocol.buildSwipeRequest(activeSession, HostAdbProtocol.newToken(18), request)
            dispatchMayHaveOccurred = true
            writeMessage(message)
            val result = readMessage()
            HostAdbProtocol.acceptResult(activeSession, message, result, "SWIPE_RESULT").second
        } catch (_: Exception) {
            close()
            InputExecutionResult(
                status = if (dispatchMayHaveOccurred) InputExecutionStatus.AMBIGUOUS else InputExecutionStatus.UNAVAILABLE,
                foreground = false,
                detailCode = if (dispatchMayHaveOccurred) "RESPONSE_LOST" else "SESSION_UNAVAILABLE",
            )
        }
    }

    private fun ensureSession(): HostProtocolSession {
        session?.let { return it }
        val connected = connector.connect(devicePort)
        socket = connected
        input = DataInputStream(connected.getInputStream())
        output = DataOutputStream(connected.getOutputStream())
        val hello =
            HostAdbProtocol.buildHello(
                pairing = configuration.pairing,
                appVersion = appVersion,
                requestId = HostAdbProtocol.newToken(18),
                clientNonce = HostAdbProtocol.newToken(24),
            )
        writeMessage(hello)
        return HostAdbProtocol.acceptHelloResult(hello, readMessage(), configuration.pairing, appVersion).also { session = it }
    }

    private fun writeMessage(message: JSONObject) {
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= HOST_MAX_FRAME_BYTES)
        val stream = output ?: throw IOException("transport is not connected")
        stream.writeInt(bytes.size)
        stream.write(bytes)
        stream.flush()
    }

    private fun readMessage(): JSONObject {
        val stream = input ?: throw IOException("transport is not connected")
        val size = stream.readInt()
        require(size in 1..HOST_MAX_FRAME_BYTES)
        val bytes = ByteArray(size)
        stream.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    @Synchronized
    override fun close() {
        session = null
        input = null
        output = null
        try {
            socket?.close()
        } catch (_: IOException) {
            // Closing a failed loopback session is best-effort; no request is retried.
        }
        socket = null
    }
}
