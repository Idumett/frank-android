package com.idumet.frank

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.idumet.frank.network.FrankClient
import com.idumet.frank.network.FrankWebSocketClient
import com.idumet.frank.service.AdaptiveSilenceDetector
import com.idumet.frank.service.AudioRecorder
import com.idumet.frank.service.FrankAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun FrankConversationHost(
    autoConnect: Boolean,
    autoStartRecording: Boolean,
    showConnectButton: Boolean,
    showServerHealthControls: Boolean,
    showCloseButton: Boolean,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val audioRecorder = remember {
        AudioRecorder(context.applicationContext)
    }

    val audioPlayer = remember {
        FrankAudioPlayer(context.applicationContext)
    }

    var isRecording by remember {
        mutableStateOf(false)
    }

    var isStreamReady by remember {
        mutableStateOf(false)
    }

    var hasAutoStarted by remember {
        mutableStateOf(false)
    }

    var recordingStatus by remember {
        mutableStateOf("Ready")
    }

    var streamStatus by remember {
        mutableStateOf(
            "Device stream disconnected"
        )
    }

    var serverStatus by remember {
        mutableStateOf("Server not tested")
    }

    val stopInProgress = remember {
        AtomicBoolean(false)
    }

    var silenceDetector:
            AdaptiveSilenceDetector? by remember {
        mutableStateOf(null)
    }

    lateinit var webSocketClient:
            FrankWebSocketClient

    fun stopAndSendRecording(
        statusBeforeSending: String
    ) {
        if (
            !isRecording ||
            !stopInProgress.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        recordingStatus = statusBeforeSending

        coroutineScope.launch {
            try {
                val audioFile =
                    withContext(Dispatchers.IO) {
                        audioRecorder.stop()
                    }

                isRecording = false
                silenceDetector?.reset()
                silenceDetector = null

                if (audioFile == null) {
                    recordingStatus =
                        "No recording created"
                    return@launch
                }

                recordingStatus = "Processing..."

                try {
                    withContext(Dispatchers.IO) {
                        webSocketClient.sendAudio(
                            audioFile
                        )
                    }
                } finally {
                    audioFile.delete()
                }
            } catch (exception: Exception) {
                isRecording = false

                recordingStatus =
                    exception.message
                        ?: "Failed to send audio"
            } finally {
                stopInProgress.set(false)
            }
        }
    }

    fun startRecording(
        useSilenceDetection: Boolean
    ) {
        if (isRecording) {
            return
        }

        if (!isStreamReady) {
            recordingStatus =
                "Wait for Frank to connect"
            return
        }

        try {
            audioPlayer.stop()
            stopInProgress.set(false)

            if (useSilenceDetection) {
                val detector =
                    AdaptiveSilenceDetector(
                        calibrationDurationMs = 500L,
                        requiredSpeechDurationMs = 350L,
                        endSilenceDurationMs = 1_600L,
                        noSpeechTimeoutMs = 8_000L,
                        maximumRecordingDurationMs = 15_000L,
                        thresholdMultiplier = 2.2,
                        minimumThreshold = 500.0,
                        thresholdMargin = 250.0,
                        onSpeechStarted = {
                            mainHandler.post {
                                recordingStatus =
                                    "Listening..."
                            }
                        },
                        onFinished = { reason ->
                            mainHandler.post {
                                val status =
                                    when (reason) {
                                        AdaptiveSilenceDetector
                                            .FinishReason
                                            .SILENCE_AFTER_SPEECH ->
                                            "Processing..."

                                        AdaptiveSilenceDetector
                                            .FinishReason
                                            .NO_SPEECH_TIMEOUT ->
                                            "No speech detected"

                                        AdaptiveSilenceDetector
                                            .FinishReason
                                            .MAXIMUM_DURATION ->
                                            "Processing..."
                                    }

                                stopAndSendRecording(
                                    status
                                )
                            }
                        }
                    )

                silenceDetector = detector

                audioRecorder
                    .setAudioSampleListener {
                            samples,
                            sampleCount ->

                        detector.process(
                            samples = samples,
                            sampleCount = sampleCount
                        )
                    }

                detector.start()
            } else {
                audioRecorder
                    .setAudioSampleListener(null)
            }

            audioRecorder.start()
            isRecording = true

            recordingStatus =
                if (useSilenceDetection) {
                    "Listening..."
                } else {
                    "Recording..."
                }
        } catch (exception: Exception) {
            isRecording = false
            silenceDetector?.reset()
            silenceDetector = null

            recordingStatus =
                exception.message
                    ?: "Recording failed"
        }
    }

    fun toggleManualRecording() {
        if (isRecording) {
            stopAndSendRecording(
                statusBeforeSending =
                    "Sending to Frank..."
            )
        } else {
            startRecording(
                useSilenceDetection = false
            )
        }
    }

    webSocketClient = remember {
        FrankWebSocketClient(
            listener =
                object :
                    FrankWebSocketClient.Listener {

                    override fun onConnecting() {
                        mainHandler.post {
                            isStreamReady = false

                            streamStatus =
                                "Connecting to Frank..."
                        }
                    }

                    override fun onReady() {
                        mainHandler.post {
                            isStreamReady = true

                            streamStatus =
                                "Frank device stream ready"
                        }
                    }

                    override fun onTranscript(
                        transcript: String
                    ) {
                        mainHandler.post {
                            recordingStatus =
                                "You: $transcript"
                        }
                    }

                    override fun onReply(
                        reply: String
                    ) {
                        mainHandler.post {
                            recordingStatus =
                                "Frank: $reply"
                        }
                    }

                    override fun onAudioReceived(
                        bytesBase64: String
                    ) {
                        mainHandler.post {
                            recordingStatus =
                                "Preparing Frank's response..."

                            audioPlayer.play(
                                bytesBase64 =
                                    bytesBase64,
                                onStarted = {
                                    recordingStatus =
                                        "Frank is speaking..."
                                },
                                onCompleted = {
                                    recordingStatus =
                                        "Ready"
                                },
                                onError = { message ->
                                    recordingStatus =
                                        message
                                }
                            )
                        }
                    }

                    override fun onError(
                        message: String
                    ) {
                        mainHandler.post {
                            isStreamReady = false

                            streamStatus =
                                "Error: $message"
                        }
                    }

                    override fun onDisconnected() {
                        mainHandler.post {
                            isStreamReady = false

                            streamStatus =
                                "Device stream disconnected"
                        }
                    }
                }
        )
    }

    LaunchedEffect(autoConnect) {
        if (autoConnect) {
            webSocketClient.connect()
        }
    }

    LaunchedEffect(
        isStreamReady,
        autoStartRecording
    ) {
        if (
            autoStartRecording &&
            isStreamReady &&
            !hasAutoStarted &&
            !isRecording
        ) {
            hasAutoStarted = true

            startRecording(
                useSilenceDetection = true
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hasAutoStarted = false
            silenceDetector?.reset()
            silenceDetector = null

            audioRecorder
                .setAudioSampleListener(null)

            if (audioRecorder.isRecording) {
                audioRecorder.cancel()
            }

            audioPlayer.release()
            webSocketClient.disconnect()
        }
    }

    fun testServerConnection() {
        coroutineScope.launch {
            serverStatus = "Connecting..."

            try {
                val response =
                    FrankClient.api.healthCheck()

                serverStatus =
                    if (response.isSuccessful) {
                        "Frank server connected"
                    } else {
                        "Server returned HTTP " +
                                response.code()
                    }
            } catch (exception: Exception) {
                serverStatus =
                    exception.message
                        ?: "Connection failed"
            }
        }
    }

    ConversationControls(
        isRecording = isRecording,
        isStreamReady = isStreamReady,
        recordingStatus = recordingStatus,
        streamStatus = streamStatus,
        serverStatus = serverStatus,
        showConnectButton =
            showConnectButton,
        showServerHealthControls =
            showServerHealthControls,
        showCloseButton =
            showCloseButton,
        onToggleRecording =
            ::toggleManualRecording,
        onConnectStream = {
            webSocketClient.connect()
        },
        onTestServer =
            ::testServerConnection,
        onClose = onClose
    )
}