package com.idumet.frank.network

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.util.Base64
import java.io.File

class FrankWebSocketClient(
    private val listener: Listener
) {

    interface Listener {
        fun onConnecting()
        fun onReady()
        fun onTranscript(transcript: String)
        fun onReply(reply: String)
        fun onAudioReceived(bytesBase64: String)
        fun onError(message: String)
        fun onDisconnected()
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var sessionId: String = createSessionId()
    private var isReady = false

    fun connect() {
        if (webSocket != null) {
            return
        }

        listener.onConnecting()

        val request = Request.Builder()
            .url(DEVICE_STREAM_URL)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    sendHello(webSocket)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    handleMessage(text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    isReady = false
                    this@FrankWebSocketClient.webSocket = null
                    listener.onDisconnected()
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    throwable: Throwable,
                    response: Response?
                ) {
                    isReady = false
                    this@FrankWebSocketClient.webSocket = null

                    val message = buildString {
                        append(throwable.message ?: "WebSocket connection failed")

                        if (response != null) {
                            append(" (HTTP ${response.code})")
                        }
                    }

                    listener.onError(message)
                }
            }
        )
    }

    fun sendAudio(file: File) {
        check(isReady) {
            "Frank device stream is not ready"
        }

        check(file.exists() && file.length() > 44) {
            "Recorded audio file is empty"
        }

        val audioBase64 = Base64.encodeToString(
            file.readBytes(),
            Base64.NO_WRAP
        )

        val message = DeviceAudioMessage(
            audioBase64 = audioBase64
        )

        val sent = webSocket?.send(
            gson.toJson(message)
        ) == true

        if (!sent) {
            throw IllegalStateException(
                "Failed to send audio to Frank"
            )
        }
    }

    private fun sendHello(webSocket: WebSocket) {
        val hello = DeviceHelloMessage(
            trace = DeviceTrace(
                requestId = createRequestId(),
                sessionId = sessionId
            ),
            device = DeviceInfo(
                model = "android",
                fw = "android-poc-v1"
            )
        )

        val sent = webSocket.send(gson.toJson(hello))

        if (!sent) {
            listener.onError("Failed to send device hello message")
        }
    }

    private fun handleMessage(json: String) {
        try {
            val message = gson.fromJson(
                json,
                DeviceStreamMessage::class.java
            )

            when (message.type) {
                "ready" -> {
                    isReady = true
                    listener.onReady()
                }

                "transcript" -> {
                    message.transcript?.let(listener::onTranscript)
                }

                "meta" -> {
                    message.reply?.speak?.let(listener::onReply)
                }

                "audio" -> {
                    message.bytesBase64?.let(listener::onAudioReceived)
                }

                "error" -> {
                    listener.onError(
                        message.message ?: "Unknown Frank server error"
                    )
                }
            }
        } catch (exception: Exception) {
            listener.onError(
                "Invalid device-stream message: ${exception.message}"
            )
        }
    }

    fun disconnect() {
        isReady = false;

        webSocket?.close(
            1000,
            "Android client closed"
        )

        webSocket = null
    }

    private fun createRequestId(): String {
        return "req-${System.currentTimeMillis()}-" +
                UUID.randomUUID().toString().take(8)
    }

    private fun createSessionId(): String {
        return "frank-android-${UUID.randomUUID()}"
    }

    companion object {
        private const val DEVICE_STREAM_URL =
            "ws://192.168.0.83:3000/v1/device/stream"
    }
}