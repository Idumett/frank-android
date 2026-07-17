package com.idumet.frank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idumet.frank.network.FrankClient
import com.idumet.frank.network.FrankWebSocketClient
import com.idumet.frank.service.AudioRecorder
import com.idumet.frank.service.FrankAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FrankAssistantScreen(
    assistantMode: Boolean = false,
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

    var serverStatus by remember {
        mutableStateOf("Server not tested")
    }

    var streamStatus by remember {
        mutableStateOf("Device stream disconnected")
    }

    val webSocketClient = remember {
        FrankWebSocketClient(
            listener = object : FrankWebSocketClient.Listener {

                override fun onConnecting() {
                    streamStatus = "Connecting..."
                }

                override fun onReady() {
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
                    streamStatus = "Error: $message"
                }

                override fun onDisconnected() {
                    streamStatus = "Device stream disconnected"
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancel()
            audioPlayer.release()
            webSocketClient.disconnect()
        }
    }

    fun toggleRecording() {
        try {
            if (!isRecording) {
                audioPlayer.stop()
                audioRecorder.start()

                isRecording = true
                recordingStatus = "Recording..."
                return
            }

            val file = audioRecorder.stop()
            isRecording = false

            if (file == null) {
                recordingStatus = "No recording created"
                return
            }

            recordingStatus = "Sending to Frank..."

            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        webSocketClient.sendAudio(file)
                    }

                    recordingStatus = "Processing..."
                } catch (exception: Exception) {
                    recordingStatus =
                        exception.message ?: "Failed to send audio"
                } finally {
                    file.delete()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Frank")

        if (!assistantMode) {
            FrankPermissionSection()
        }

        FrankConversationSection(
            isRecording = isRecording,
            recordingStatus = recordingStatus,
            serverStatus = serverStatus,
            streamStatus = streamStatus,
            onToggleRecording = ::toggleRecording,
            onTestServer = ::testServerConnection,
            onConnectStream = webSocketClient::connect
        )

        if (assistantMode) {
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = onClose
            ) {
                Text("Close")
            }
        }
    }
}