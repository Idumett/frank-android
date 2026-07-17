package com.idumet.frank

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
import com.idumet.frank.service.AudioRecorder
import com.idumet.frank.service.FrankAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val audioRecorder = remember {
        AudioRecorder(context.applicationContext)
    }

    val audioPlayer = remember {
        FrankAudioPlayer(context.applicationContext)
    }

    var isRecording by remember {
        mutableStateOf(false)
    }

    var recordingStatus by remember {
        mutableStateOf("Ready")
    }

    var hasAutoStarted by remember {
        mutableStateOf(false)
    }

    var streamStatus by remember {
        mutableStateOf("Device stream disconnected")
    }

    var serverStatus by remember {
        mutableStateOf("Server not tested")
    }

    var isStreamReady by remember {
        mutableStateOf(false)
    }

    val webSocketClient = remember {
        FrankWebSocketClient(
            listener = object : FrankWebSocketClient.Listener {

                override fun onConnecting() {
                    isStreamReady = false
                    streamStatus = "Connecting to Frank..."
                }

                override fun onReady() {
                    isStreamReady = true
                    streamStatus = "Frank device stream ready"
                }

                override fun onTranscript(transcript: String) {
                    recordingStatus = "You: $transcript"
                }

                override fun onReply(reply: String) {
                    recordingStatus = "Frank: $reply"
                }

                override fun onAudioReceived(bytesBase64: String) {
                    recordingStatus = "Preparing Frank's response..."

                    audioPlayer.play(
                        bytesBase64 = bytesBase64,
                        onStarted = {
                            recordingStatus = "Frank is speaking..."
                        },
                        onCompleted = {
                            recordingStatus = "Ready"
                        },
                        onError = { message ->
                            recordingStatus = message
                        }
                    )
                }

                override fun onError(message: String) {
                    isStreamReady = false
                    streamStatus = "Error: $message"
                }

                override fun onDisconnected() {
                    isStreamReady = false
                    streamStatus = "Device stream disconnected"
                }
            }
        )
    }

    LaunchedEffect(autoConnect) {
        if (autoConnect) {
            webSocketClient.connect()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hasAutoStarted = false

            if (isRecording) {
                audioRecorder.cancel()
            }

            audioPlayer.release()
            webSocketClient.disconnect()
        }
    }

    fun toggleRecording() {
        if (!isStreamReady) {
            recordingStatus = "Wait for Frank to connect"
            return
        }

        try {
            if (!isRecording) {
                audioPlayer.stop()
                audioRecorder.start()

                isRecording = true
                recordingStatus = "Listening..."
                return
            }

            val audioFile = audioRecorder.stop()
            isRecording = false

            if (audioFile == null) {
                recordingStatus = "No recording created"
                return
            }

            recordingStatus = "Processing..."

            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        webSocketClient.sendAudio(audioFile)
                    }

                    recordingStatus = "Processing..."
                } catch (exception: Exception) {
                    recordingStatus =
                        exception.message ?: "Failed to send audio"
                } finally {
                    audioFile.delete()
                }
            }
        } catch (exception: Exception) {
            isRecording = false

            recordingStatus =
                exception.message ?: "Recording failed"
        }
    }

    fun testServerConnection() {
        coroutineScope.launch {
            serverStatus = "Connecting..."

            try {
                val response = FrankClient.api.healthCheck()

                serverStatus =
                    if (response.isSuccessful) {
                        "Frank server connected"
                    } else {
                        "Server returned HTTP ${response.code()}"
                    }
            } catch (exception: Exception) {
                serverStatus =
                    exception.message ?: "Connection failed"
            }
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

            toggleRecording()
        }
    }

    ConversationControls(
        isRecording = isRecording,
        isStreamReady = isStreamReady,
        recordingStatus = recordingStatus,
        streamStatus = streamStatus,
        serverStatus = serverStatus,
        showConnectButton = showConnectButton,
        showServerHealthControls = showServerHealthControls,
        showCloseButton = showCloseButton,
        onToggleRecording = ::toggleRecording,
        onConnectStream = {
            webSocketClient.connect()
        },
        onTestServer = ::testServerConnection,
        onClose = onClose
    )
}