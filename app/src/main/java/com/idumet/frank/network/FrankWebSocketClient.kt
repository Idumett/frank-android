package com.idumet.frank.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

        fun onFrameAcknowledged(seq: Int) {
            // Optional callback.
        }
    }

    private data class DeviceFrameMessage(
        val type: String = "frame",
        val seq: Int,
        val mime: String,
        val bytesBase64: String
    )

    private val gson = Gson()

    private val client =
        OkHttpClient.Builder()
            .pingInterval(
                20,
                TimeUnit.SECONDS
            )
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .build()

    private val frameSequence =
        AtomicInteger(0)

    private var webSocket: WebSocket? = null

    private var sessionId: String =
        createSessionId()

    @Volatile
    private var isReady = false

    fun connect() {
        if (webSocket != null) {
            return
        }

        listener.onConnecting()

        val request =
            Request.Builder()
                .url(DEVICE_STREAM_URL)
                .build()

        webSocket =
            client.newWebSocket(
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
                        webSocket.close(
                            code,
                            reason
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        isReady = false

                        this@FrankWebSocketClient
                            .webSocket = null

                        listener.onDisconnected()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        throwable: Throwable,
                        response: Response?
                    ) {
                        isReady = false

                        this@FrankWebSocketClient
                            .webSocket = null

                        val message =
                            buildString {
                                append(
                                    throwable.message
                                        ?: "WebSocket connection failed"
                                )

                                if (response != null) {
                                    append(
                                        " (HTTP ${response.code})"
                                    )
                                }
                            }

                        listener.onError(message)
                    }
                }
            )
    }

    /**
     * Sends the latest camera image to Frank.
     *
     * The server stores this as the current frame and uses it when the
     * following utterance or audio message is processed.
     *
     * WebSocket messages are delivered in order, so calling sendFrame()
     * immediately before sendAudio() guarantees the frame arrives first.
     *
     * @return the frame sequence number.
     */
    fun sendFrame(
        file: File
    ): Int {
        check(isReady) {
            "Frank device stream is not ready"
        }

        check(
            file.exists() &&
                    file.isFile &&
                    file.length() > 0L
        ) {
            "Camera frame is empty"
        }

        val mimeType =
            determineImageMimeType(file)

        val frameBase64 =
            Base64.encodeToString(
                file.readBytes(),
                Base64.NO_WRAP
            )

        val sequence =
            frameSequence.getAndIncrement()

        val message =
            DeviceFrameMessage(
                seq = sequence,
                mime = mimeType,
                bytesBase64 = frameBase64
            )

        val sent =
            webSocket?.send(
                gson.toJson(message)
            ) == true

        if (!sent) {
            throw IllegalStateException(
                "Failed to send camera frame to Frank"
            )
        }

        return sequence
    }

    fun sendAudio(
        file: File
    ) {
        check(isReady) {
            "Frank device stream is not ready"
        }

        check(
            file.exists() &&
                    file.length() > WAV_HEADER_SIZE
        ) {
            "Recorded audio file is empty"
        }

        val audioBase64 =
            Base64.encodeToString(
                file.readBytes(),
                Base64.NO_WRAP
            )

        val message =
            DeviceAudioMessage(
                audioBase64 = audioBase64
            )

        val sent =
            webSocket?.send(
                gson.toJson(message)
            ) == true

        if (!sent) {
            throw IllegalStateException(
                "Failed to send audio to Frank"
            )
        }
    }

    /**
     * Sends an image followed by an audio recording.
     *
     * The image is optional, allowing voice-only requests to continue
     * working if camera permission is unavailable or no frame exists.
     */
    fun sendRequest(
        audioFile: File,
        imageFile: File?
    ): Int? {
        val frameSequence =
            imageFile
                ?.takeIf {
                    it.exists() &&
                            it.length() > 0L
                }
                ?.let(::sendFrame)

        sendAudio(audioFile)

        return frameSequence
    }

    private fun sendHello(
        webSocket: WebSocket
    ) {
        val hello =
            DeviceHelloMessage(
                trace =
                    DeviceTrace(
                        requestId =
                            createRequestId(),
                        sessionId =
                            sessionId
                    ),
                device =
                    DeviceInfo(
                        model = "android",
                        fw = "android-poc-v2"
                    )
            )

        val sent =
            webSocket.send(
                gson.toJson(hello)
            )

        if (!sent) {
            listener.onError(
                "Failed to send device hello message"
            )
        }
    }

    private fun handleMessage(
        json: String
    ) {
        try {
            val jsonObject =
                JsonParser.parseString(json)
                    .asJsonObject

            val type =
                jsonObject
                    .get("type")
                    ?.asString

            when (type) {
                "ready" -> {
                    isReady = true
                    listener.onReady()
                }

                "ack" -> {
                    val sequence =
                        jsonObject
                            .get("seq")
                            ?.asInt

                    if (sequence != null) {
                        listener.onFrameAcknowledged(
                            sequence
                        )
                    }
                }

                "transcript" -> {
                    val transcript =
                        jsonObject
                            .get("transcript")
                            ?.takeUnless {
                                it.isJsonNull
                            }
                            ?.asString

                    if (!transcript.isNullOrBlank()) {
                        listener.onTranscript(
                            transcript
                        )
                    }
                }

                "meta" -> {
                    val reply =
                        jsonObject
                            .getAsJsonObject("reply")
                            ?.get("speak")
                            ?.takeUnless {
                                it.isJsonNull
                            }
                            ?.asString

                    if (!reply.isNullOrBlank()) {
                        listener.onReply(reply)
                    }
                }

                "audio" -> {
                    val bytesBase64 =
                        jsonObject
                            .get("bytesBase64")
                            ?.takeUnless {
                                it.isJsonNull
                            }
                            ?.asString

                    if (!bytesBase64.isNullOrBlank()) {
                        listener.onAudioReceived(
                            bytesBase64
                        )
                    }
                }

                "error" -> {
                    val message =
                        jsonObject
                            .get("message")
                            ?.takeUnless {
                                it.isJsonNull
                            }
                            ?.asString
                            ?: "Unknown Frank server error"

                    listener.onError(message)
                }

                "stopped",
                "pong" -> {
                    // No UI action required.
                }

                else -> {
                    // Ignore future server message types that this client
                    // does not need yet.
                }
            }
        } catch (exception: Exception) {
            listener.onError(
                "Invalid device-stream message: " +
                        exception.message
            )
        }
    }

    fun disconnect() {
        isReady = false

        webSocket?.close(
            1000,
            "Android client closed"
        )

        webSocket = null
    }

    private fun determineImageMimeType(
        file: File
    ): String {
        return when (
            file.extension.lowercase()
        ) {
            "jpg",
            "jpeg" -> "image/jpeg"

            "png" -> "image/png"

            "webp" -> "image/webp"

            else -> throw IllegalArgumentException(
                "Unsupported camera frame format: " +
                        file.extension
            )
        }
    }

    private fun createRequestId(): String {
        return "req-${System.currentTimeMillis()}-" +
                UUID.randomUUID()
                    .toString()
                    .take(8)
    }

    private fun createSessionId(): String {
        return "frank-android-" +
                UUID.randomUUID()
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44L

        private const val DEVICE_STREAM_URL =
            "ws://192.168.0.83:3000/v1/device/stream"
    }
}