package com.idumet.frank.network

import com.idumet.frank.BuildConfig
import com.idumet.frank.camera.CameraFrame
import com.idumet.frank.context.ContextSnapshotV1
import com.idumet.frank.context.ContextSnapshotValidatorV1

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

        fun onFrameRequested(trace: DeviceTrace, maxAgeMs: Long, frameRequestId: String) {
            // Optional callback for clients without a camera.
        }

        fun onFrameAcknowledged(seq: Int) {
            // Optional callback.
        }
    }

    private data class DeviceFrameMessage(
        val type: String = "frame",
        val trace: DeviceTrace,
        val frameRequestId: String,
        val frameId: String,
        val seq: Int,
        val mime: String,
        val bytesBase64: String,
        val capturedAt: String,
        val ageMs: Long,
        val quality: String
    )

    private data class ContextSnapshotMessage(
        val type: String = "context_snapshot",
        val snapshot: ContextSnapshotV1
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

    private val activeRequestId = AtomicReference<String?>(null)

    private val evidenceCalls = ConcurrentHashMap<String, Call>()

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
                    if (BuildConfig.FRANK_ACCESS_TOKEN.isNotBlank()) {
                        header("Authorization", "Bearer ${BuildConfig.FRANK_ACCESS_TOKEN}")
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
                        if (text.length > MAX_SERVER_MESSAGE_CHARS) {
                            listener.onError("Frank returned an oversized response")
                            webSocket.close(1009, "Response too large")
                            return
                        }
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
        activeRequestId.set(null)
        cancelEvidenceUploads()
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

    /** Sends one frame after the server requests evidence for this turn. */
    fun sendFrame(
        frame: CameraFrame,
        trace: DeviceTrace,
        frameRequestId: String
    ): Int {
        check(isReady) {
            "Frank device stream is not ready"
        }

        check(
            frame.file.exists() &&
                    frame.file.isFile &&
                    frame.file.length() in 1..MAX_IMAGE_BYTES
        ) {
            "Camera frame is empty"
        }

        val mimeType =
            determineImageMimeType(frame.file)

        val frameBase64 =
            Base64.encodeToString(
                frame.file.readBytes(),
                Base64.NO_WRAP
            )

        val sequence =
            frameSequence.getAndIncrement()

        val message =
            DeviceFrameMessage(
                trace = trace,
                frameRequestId = frameRequestId,
                frameId = frame.frameId,
                seq = sequence,
                mime = mimeType,
                bytesBase64 = frameBase64,
                capturedAt = Instant.ofEpochMilli(frame.capturedAtMs).toString(),
                ageMs = (System.currentTimeMillis() - frame.capturedAtMs).coerceAtLeast(0L),
                quality = frame.quality
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
            "frame_sent requestId=${trace.requestId} frameId=${frame.frameId} seq=$sequence bytes=${frame.file.length()}"
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
                    file.length() in (WAV_HEADER_SIZE + 1)..MAX_AUDIO_BYTES
        ) {
            "Recorded audio is empty or too large"
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

    fun currentSessionId(): String = sessionId

    fun sendContextSnapshot(snapshot: ContextSnapshotV1) {
        check(isReady) { "Frank device stream is not ready" }
        check(snapshot.sessionId == sessionId) { "Context snapshot belongs to another session" }
        ContextSnapshotValidatorV1.validate(snapshot)
        check(webSocket?.send(gson.toJson(ContextSnapshotMessage(snapshot = snapshot))) == true) {
            "Failed to send context snapshot to Frank"
        }
        Log.d(LOG_TAG, "context_sent snapshotId=${snapshot.snapshotId}")
    }

    /**
     * Sends an image followed by an audio recording.
     *
     * The image is optional, allowing voice-only requests to continue
     * working if camera permission is unavailable or no frame exists.
     */
    fun sendRequest(
        audioFile: File,
        evidenceFrame: CameraFrame? = null
    ): String {
        val trace =
            DeviceTrace(
                requestId = createRequestId(),
                sessionId = sessionId
            )

        activeRequestId.set(trace.requestId)
        requestStartedAt[trace.requestId] = SystemClock.elapsedRealtime()

        try {
            sendAudio(audioFile, trace)
            evidenceFrame?.let { uploadPreparedEvidence(it, trace) }
            return trace.requestId
        } catch (exception: Exception) {
            requestStartedAt.remove(trace.requestId)
            activeRequestId.compareAndSet(trace.requestId, null)
            throw exception
        }
    }

    private fun uploadPreparedEvidence(frame: CameraFrame, trace: DeviceTrace) {
        val mime = determineImageMimeType(frame.file)
        val bytes = frame.file.readBytes()
        check(bytes.size.toLong() in 1..MAX_IMAGE_BYTES) { "Camera frame is empty" }
        val request = Request.Builder()
            .url(evidenceUploadUrl(trace.requestId))
            .header("X-Frank-Session-Id", sessionId)
            .header("X-Frank-Frame-Id", frame.frameId)
            .header("X-Frank-Captured-At", Instant.ofEpochMilli(frame.capturedAtMs).toString())
            .header("X-Frank-Frame-Age-Ms", (System.currentTimeMillis() - frame.capturedAtMs).coerceAtLeast(0L).toString())
            .header("X-Frank-Frame-Quality", frame.quality)
            .apply {
                if (BuildConfig.FRANK_ACCESS_TOKEN.isNotBlank()) {
                    header("Authorization", "Bearer ${BuildConfig.FRANK_ACCESS_TOKEN}")
                }
            }
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val call = client.newCall(request)
        evidenceCalls[trace.requestId] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                evidenceCalls.remove(trace.requestId, call)
                if (call.isCanceled()) return
                Log.w(LOG_TAG, "evidence_upload_failed requestId=${trace.requestId}", exception)
            }

            override fun onResponse(call: Call, response: Response) {
                evidenceCalls.remove(trace.requestId, call)
                response.use {
                    Log.d(LOG_TAG, "evidence_uploaded requestId=${trace.requestId} status=${it.code} bytes=${bytes.size}")
                }
            }
        })
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

                "frame_required" -> {
                    val trace = gson.fromJson(jsonObject.get("trace"), DeviceTrace::class.java)
                    if (!isCurrentRequest(trace.requestId)) return
                    val maxAgeMs = jsonObject.get("maxAgeMs")?.asLong ?: DEFAULT_FRAME_MAX_AGE_MS
                    val frameRequestId = jsonObject.get("frameRequestId")?.asString
                        ?: throw IllegalStateException("Camera request has no identity")
                    listener.onFrameRequested(trace, maxAgeMs, frameRequestId)
                }

                "context_ack" -> {
                    Log.d(LOG_TAG, "context_accepted snapshotId=${jsonObject.get("snapshotId")?.asString}")
                }

                "transcript" -> {
                    if (!isCurrentResponse(jsonObject)) return
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
                    if (!isCurrentResponse(jsonObject)) return
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
                    if (!isCurrentResponse(jsonObject)) return
                    logRoundTrip(jsonObject, "audio_received", complete = true)

                    val bytesBase64 =
                        jsonObject
                            .get("bytesBase64")
                            ?.takeUnless {
                                it.isJsonNull
                            }
                            ?.asString

                    if (!bytesBase64.isNullOrBlank()) {
                        check(bytesBase64.length <= MAX_AUDIO_BASE64_CHARS) {
                            "Frank returned oversized audio"
                        }
                        listener.onAudioReceived(
                            bytesBase64
                        )
                        activeRequestId.compareAndSet(requestId(jsonObject), null)
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
                    if (requestId != null && !isCurrentRequest(requestId)) return
                    Log.e(LOG_TAG, "server_error requestId=$requestId message=$message")
                    if (requestId != null) requestStartedAt.remove(requestId)
                    activeRequestId.compareAndSet(requestId, null)
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

    private fun isCurrentResponse(jsonObject: com.google.gson.JsonObject): Boolean {
        val requestId = requestId(jsonObject) ?: return false
        return isCurrentRequest(requestId)
    }

    private fun isCurrentRequest(requestId: String): Boolean =
        activeRequestId.get() == requestId

    fun stop() {
        val sent = webSocket?.send(
            gson.toJson(mapOf("type" to "stop"))
        ) == true

        if (!sent && isReady) {
            listener.onError("Failed to stop the active Frank request")
        }

        activeRequestId.getAndSet(null)?.let {
            requestStartedAt.remove(it)
            evidenceCalls.remove(it)?.cancel()
        }
    }

    @Synchronized
    fun disconnect() {
        keepConnected = false
        isReady = false
        reconnectAttempt = 0
        requestStartedAt.clear()
        activeRequestId.set(null)
        cancelEvidenceUploads()
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

    private fun cancelEvidenceUploads() {
        evidenceCalls.values.forEach(Call::cancel)
        evidenceCalls.clear()
    }

    companion object {
        private const val LOG_TAG = "FrankDiagnostics"
        private const val WAV_HEADER_SIZE = 44L
        private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
        private const val MAX_AUDIO_BYTES = 8L * 1024L * 1024L
        private const val MAX_AUDIO_BASE64_CHARS = 12 * 1024 * 1024
        private const val MAX_SERVER_MESSAGE_CHARS = 13 * 1024 * 1024
        private const val DEFAULT_FRAME_MAX_AGE_MS = 4_000L

        private fun deviceStreamUrl(): String {
            return FrankEndpoint.deviceStream(
                BuildConfig.FRANK_SERVER_URL,
                releaseBuild = !BuildConfig.DEBUG
            )
        }

        private fun evidenceUploadUrl(requestId: String): String =
            FrankEndpoint.evidenceUpload(
                BuildConfig.FRANK_SERVER_URL,
                requestId,
                releaseBuild = !BuildConfig.DEBUG
            )
    }
}
