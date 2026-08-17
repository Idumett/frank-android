package com.idumet.frank.network

import com.idumet.frank.BuildConfig

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
        val trace: DeviceTrace,
        val seq: Int,
        val mime: String,
        val bytesBase64: String,
        val capturedAt: String
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

    private val requestStartedAt =
        ConcurrentHashMap<String, Long>()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    @Volatile
    private var webSocket: WebSocket? = null

    private var sessionId: String =
        createSessionId()

    @Volatile
    private var isReady = false

    @Volatile
    private var keepConnected = false

    private var reconnectAttempt = 0

    private val reconnect = Runnable {
        openSocket()
    }

    fun connect() {
        keepConnected = true
        mainHandler.removeCallbacks(reconnect)
        openSocket()
    }

    @Synchronized
    private fun openSocket() {
        if (!keepConnected || webSocket != null) {
            return
        }

        listener.onConnecting()

        val request =
            Request.Builder()
                .url(deviceStreamUrl())
                .apply {
                    if (BuildConfig.FRANK_DEMO_TOKEN.isNotBlank()) {
                        header("Authorization", "Bearer ${BuildConfig.FRANK_DEMO_TOKEN}")
                    }
                }
                .build()

        val socketListener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    if (
                        this@FrankWebSocketClient.webSocket === webSocket &&
                        keepConnected
                    ) {
                        sendHello(webSocket)
                    } else {
                        webSocket.close(1000, "Connection no longer needed")
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    if (this@FrankWebSocketClient.webSocket === webSocket) {
                        handleMessage(text)
                    }
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
                    connectionEnded(webSocket)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    throwable: Throwable,
                    response: Response?
                ) {
                    response?.close()
                    val message =
                        buildString {
                            append(throwable.message ?: "WebSocket connection failed")
                            if (response != null) append(" (HTTP ${response.code})")
                        }
                    Log.w(LOG_TAG, "stream_failed message=$message")
                    listener.onError(message)
                    connectionEnded(webSocket)
                }
            }

        webSocket = client.newWebSocket(request, socketListener)
    }

    @Synchronized
    private fun connectionEnded(
        endedSocket: WebSocket
    ) {
        if (webSocket !== endedSocket) {
            return
        }

        webSocket = null
        isReady = false
        requestStartedAt.clear()
        listener.onDisconnected()

        if (keepConnected) {
            val delayMs =
                RetryDelay.milliseconds(
                    reconnectAttempt++
                )
            Log.d(LOG_TAG, "stream_reconnect delayMs=$delayMs")
            mainHandler.removeCallbacks(reconnect)
            mainHandler.postDelayed(reconnect, delayMs)
        }
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
        file: File,
        trace: DeviceTrace
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
                trace = trace,
                seq = sequence,
                mime = mimeType,
                bytesBase64 = frameBase64,
                capturedAt = Instant.ofEpochMilli(
                    file.lastModified()
                ).toString()
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

        Log.d(
            LOG_TAG,
            "frame_sent requestId=${trace.requestId} seq=$sequence bytes=${file.length()}"
        )

        return sequence
    }

    fun sendAudio(
        file: File,
        trace: DeviceTrace
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
                trace = trace,
                audioBase64 = audioBase64,
                sentAt = Instant.now().toString()
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

        Log.d(
            LOG_TAG,
            "audio_sent requestId=${trace.requestId} bytes=${file.length()}"
        )
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
    ): String {
        val trace =
            DeviceTrace(
                requestId = createRequestId(),
                sessionId = sessionId
            )

        requestStartedAt[trace.requestId] =
            SystemClock.elapsedRealtime()

        try {
            imageFile
                ?.takeIf {
                    it.exists() &&
                            it.length() > 0L
                }
                ?.let { sendFrame(it, trace) }

            sendAudio(audioFile, trace)
            return trace.requestId
        } catch (exception: Exception) {
            requestStartedAt.remove(trace.requestId)
            throw exception
        }
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
                    reconnectAttempt = 0
                    isReady = true
                    Log.d(LOG_TAG, "stream_ready sessionId=$sessionId")
                    listener.onReady()
                }

                "ack" -> {
                    val sequence =
                        jsonObject
                            .get("seq")
                            ?.asInt

                    if (sequence != null) {
                        Log.d(LOG_TAG, "frame_ack seq=$sequence")
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
                    logRoundTrip(jsonObject, "reply_received")

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
                    logRoundTrip(jsonObject, "audio_received", complete = true)

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

                    val requestId = requestId(jsonObject)
                    Log.e(LOG_TAG, "server_error requestId=$requestId message=$message")
                    if (requestId != null) requestStartedAt.remove(requestId)
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

    private fun logRoundTrip(
        jsonObject: com.google.gson.JsonObject,
        event: String,
        complete: Boolean = false
    ) {
        val requestId = requestId(jsonObject) ?: return
        val startedAt = requestStartedAt[requestId] ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        Log.d(LOG_TAG, "$event requestId=$requestId elapsedMs=$elapsedMs")
        if (complete) requestStartedAt.remove(requestId)
    }

    private fun requestId(
        jsonObject: com.google.gson.JsonObject
    ): String? =
        jsonObject
            .getAsJsonObject("trace")
            ?.get("requestId")
            ?.takeUnless { it.isJsonNull }
            ?.asString

    fun stop() {
        val sent = webSocket?.send(
            gson.toJson(mapOf("type" to "stop"))
        ) == true

        if (!sent && isReady) {
            listener.onError("Failed to stop the active Frank request")
        }
    }

    @Synchronized
    fun disconnect() {
        keepConnected = false
        isReady = false
        reconnectAttempt = 0
        requestStartedAt.clear()
        mainHandler.removeCallbacks(reconnect)

        val socket = webSocket
        webSocket = null
        socket?.close(
            1000,
            "Android client closed"
        )
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
        private const val LOG_TAG = "FrankDiagnostics"
        private const val WAV_HEADER_SIZE = 44L

        private fun deviceStreamUrl(): String {
            val serverUrl = BuildConfig.FRANK_SERVER_URL.trimEnd('/')
            val websocketBase = when {
                serverUrl.startsWith("https://") -> "wss://${serverUrl.removePrefix("https://")}"
                serverUrl.startsWith("http://") -> "ws://${serverUrl.removePrefix("http://")}"
                else -> error("Frank server URL must start with http:// or https://")
            }
            return "$websocketBase/v1/device/stream"
        }
    }
}
