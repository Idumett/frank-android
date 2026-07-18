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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.idumet.frank.camera.FrankCamera
import com.idumet.frank.network.FrankClient
import com.idumet.frank.network.FrankWebSocketClient
import com.idumet.frank.service.AdaptiveSilenceDetector
import com.idumet.frank.service.AudioRecorder
import com.idumet.frank.service.FrankAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun FrankConversationHost(
    autoConnect: Boolean,
    autoStartRecording: Boolean,
    showConnectButton: Boolean,
    showServerHealthControls: Boolean,
    showCloseButton: Boolean,
    onAssistantStateChanged: (
        AssistantState
    ) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    val haptics = remember {
        FrankHaptics(context.applicationContext)
    }

    var lastHapticState by remember {
        mutableStateOf<AssistantState?>(null)
    }

    fun updateAssistantState(
        state: AssistantState
    ) {
        if (lastHapticState != state) {
            lastHapticState = state
            haptics.performStateCue(state)
        }

        onAssistantStateChanged(state)
    }

    /*
     * Camera capture is enabled automatically for the assistant overlay.
     *
     * The debug screen currently passes autoStartRecording = false,
     * so camera capture remains disabled there.
     */
    val cameraEnabled = autoStartRecording

    var isRecording by remember {
        mutableStateOf(false)
    }

    var isStreamReady by remember {
        mutableStateOf(false)
    }

    var hasAutoStarted by remember {
        mutableStateOf(false)
    }

    var hasCameraFrame by remember {
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

    var silenceDetector by remember {
        mutableStateOf<AdaptiveSilenceDetector?>(
            null
        )
    }

    val stopInProgress = remember {
        AtomicBoolean(false)
    }

    val camera = remember {
        FrankCamera(
            context = context.applicationContext,
            captureIntervalMs = 1_000L,
            jpegQuality = 80,
            onFrameCaptured = {
                hasCameraFrame = true
            },
            onError = { message ->
                /*
                 * Camera failure should not prevent voice-only use.
                 * Frank can still process the recorded audio.
                 */
                hasCameraFrame = false

                if (!isRecording) {
                    recordingStatus =
                        "Camera unavailable: $message"
                }
            }
        )
    }

    lateinit var webSocketClient:
            FrankWebSocketClient

    fun clearSilenceDetection() {
        silenceDetector?.reset()
        silenceDetector = null

        audioRecorder.setAudioSampleListener(
            null
        )
    }

    fun deleteTemporaryFiles(
        audioFile: File?,
        imageFile: File?
    ) {
        audioFile?.delete()
        imageFile?.delete()
    }

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

        updateAssistantState(
            AssistantState.Thinking
        )

        coroutineScope.launch {
            var audioFile: File? = null
            var imageFile: File? = null

            try {
                /*
                 * Stop audio first so no additional samples are written while
                 * the WAV file is being finalised.
                 */
                audioFile =
                    withContext(Dispatchers.IO) {
                        audioRecorder.stop()
                    }

                isRecording = false
                clearSilenceDetection()

                if (audioFile == null) {
                    recordingStatus =
                        "No recording created"

                    return@launch
                }

                /*
                 * copyLatestFrame() returns a separate temporary file.
                 * The camera can therefore continue replacing its internal
                 * latest frame safely.
                 */
                imageFile =
                    if (cameraEnabled) {
                        withContext(Dispatchers.IO) {
                            camera.copyLatestFrame()
                        }
                    } else {
                        null
                    }

                recordingStatus =
                    if (imageFile != null) {
                        "Sending audio and image..."
                    } else {
                        "Sending audio..."
                    }

                withContext(Dispatchers.IO) {
                    webSocketClient.sendRequest(
                        audioFile = audioFile,
                        imageFile = imageFile
                    )
                }

                recordingStatus = "Thinking..."
            } catch (exception: Exception) {
                isRecording = false
                clearSilenceDetection()
                updateAssistantState(AssistantState.Error)

                recordingStatus =
                    exception.message
                        ?: "Failed to send request"
            } finally {
                deleteTemporaryFiles(
                    audioFile = audioFile,
                    imageFile = imageFile
                )

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
            updateAssistantState(AssistantState.Listening)

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
                                recordingStatus = "Listening..."

                                updateAssistantState(
                                    AssistantState.Listening
                                )
                            }
                        },
                        onFinished = { reason ->
                            mainHandler.post {
                                when (reason) {
                                    AdaptiveSilenceDetector
                                        .FinishReason
                                        .SILENCE_AFTER_SPEECH -> {
                                        stopAndSendRecording(
                                            statusBeforeSending =
                                                "Speech complete"
                                        )
                                    }

                                    AdaptiveSilenceDetector
                                        .FinishReason
                                        .MAXIMUM_DURATION -> {
                                        stopAndSendRecording(
                                            statusBeforeSending =
                                                "Maximum recording reached"
                                        )
                                    }

                                    AdaptiveSilenceDetector
                                        .FinishReason
                                        .NO_SPEECH_TIMEOUT -> {
                                        stopAndSendRecording(
                                            statusBeforeSending =
                                                "No speech detected"
                                        )
                                    }
                                }
                            }
                        }
                    )

                silenceDetector = detector

                audioRecorder.setAudioSampleListener {
                        samples,
                        sampleCount ->

                    detector.process(
                        samples = samples,
                        sampleCount = sampleCount
                    )
                }

                detector.start()
            } else {
                clearSilenceDetection()
            }

            audioRecorder.start()
            isRecording = true

            recordingStatus =
                when {
                    useSilenceDetection &&
                            cameraEnabled &&
                            hasCameraFrame ->
                        "Listening with camera..."

                    useSilenceDetection ->
                        "Listening..."

                    else ->
                        "Recording..."
                }
        } catch (exception: Exception) {
            isRecording = false
            clearSilenceDetection()
            updateAssistantState(AssistantState.Error)

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
                            streamStatus = "Connecting to Frank..."
                            updateAssistantState(AssistantState.Idle)
                        }
                    }

                    override fun onReady() {
                        mainHandler.post {
                            isStreamReady = true
                            streamStatus = "Frank device stream ready"
                            updateAssistantState(AssistantState.Idle)
                        }
                    }

                    override fun onTranscript(
                        transcript: String
                    ) {
                        mainHandler.post {
                            recordingStatus = "You: $transcript"
                            updateAssistantState(AssistantState.Thinking)
                        }
                    }

                    override fun onReply(
                        reply: String
                    ) {
                        mainHandler.post {
                            recordingStatus = "Frank: $reply"
                            updateAssistantState(AssistantState.Thinking)
                        }
                    }

                    override fun onAudioReceived(
                        bytesBase64: String
                    ) {
                        mainHandler.post {
                            recordingStatus = "Preparing Frank's response..."
                            updateAssistantState(AssistantState.Thinking)

                            audioPlayer.play(
                                bytesBase64 = bytesBase64,
                                onStarted = {
                                    recordingStatus = "Frank is speaking..."
                                    updateAssistantState(AssistantState.Speaking)
                                },
                                onCompleted = {
                                    recordingStatus = "Ready"
                                    updateAssistantState(AssistantState.Idle)
                                },
                                onError = { message ->
                                    recordingStatus = message
                                    updateAssistantState(AssistantState.Error)
                                }
                            )
                        }
                    }

                    override fun onFrameAcknowledged(
                        seq: Int
                    ) {
                        /*
                         * No visual update is required.
                         *
                         * The acknowledgement confirms the server has stored
                         * the frame. WebSocket ordering already ensures that
                         * the image message precedes the audio message.
                         */
                    }

                    override fun onError(
                        message: String
                    ) {
                        mainHandler.post {
                            streamStatus = "Error: $message"

                            if (!isRecording) {
                                recordingStatus = "Error: $message"
                            }

                            updateAssistantState(AssistantState.Error)
                        }
                    }

                    override fun onDisconnected() {
                        mainHandler.post {
                            isStreamReady = false
                            streamStatus = "Device stream disconnected"
                            updateAssistantState(AssistantState.Idle)
                        }
                    }
                }
        )
    }

    /*
     * Start continuous camera capture as soon as the assistant overlay is
     * composed. The newest local JPEg is replaced approximately every second.
     */
    DisposableEffect(
        cameraEnabled,
        lifecycleOwner
    ) {
        if (cameraEnabled) {
            camera.start(
                lifecycleOwner = lifecycleOwner
            )
        }

        onDispose {
            camera.release()
        }
    }

    LaunchedEffect(autoConnect) {
        if (autoConnect) {
            webSocketClient.connect()
        }
    }

    /*
     * Wait for the WebSocket before automatically recording.
     *
     * Camera capture begins independently, so in most cases a recent frame
     * already exists when the user finishes speaking.
     */
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

            clearSilenceDetection()

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