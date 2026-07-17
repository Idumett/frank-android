package com.idumet.frank

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.idumet.frank.network.FrankClient
import com.idumet.frank.network.FrankWebSocketClient
import com.idumet.frank.network.SttClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.idumet.frank.service.AudioRecorder
import com.idumet.frank.permissions.PermissionManager
import com.idumet.frank.ui.theme.FrankTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FrankTheme {
                PermissionScreen()
            }
        }
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current

    val audioRecorder = remember {
        AudioRecorder(context.applicationContext)
    }

    val isRecording = remember {
        mutableStateOf(false)
    }

    val recordingStatus = remember {
        mutableStateOf("Ready")
    }

    val coroutineScope = rememberCoroutineScope()

    val serverStatus = remember {
        mutableStateOf("Server not tested")
    }

    val streamStatus = remember {
        mutableStateOf("Device stream disconnected")
    }

    val webSocketClient = remember {
        FrankWebSocketClient(
            listener = object : FrankWebSocketClient.Listener {

                override fun onConnecting() {
                    streamStatus.value = "Connecting..."
                }

                override fun onReady() {
                    streamStatus.value = "Frank device stream ready"
                }

                override fun onTranscript(transcript: String) {
                    recordingStatus.value = "You: $transcript"
                }

                override fun onReply(reply: String) {
                    recordingStatus.value = "Frank: $reply"
                }

                override fun onAudioReceived(bytesBase64: String) {
                    recordingStatus.value = "Frank audio received"
                }

                override fun onError(message: String) {
                    streamStatus.value = "Error: $message"
                }

                override fun onDisconnected() {
                    streamStatus.value = "Device stream disconnected"
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancel()
            webSocketClient.disconnect()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val allGranted = permissions.values.all { granted ->
            granted
        }

        Toast.makeText(
            context,
            if (allGranted) {
                "Frank is ready"
            } else {
                "Microphone and camera permissions are required"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Frank needs microphone and camera access.")

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                permissionLauncher.launch(
                    PermissionManager.requiredPermissions()
                )
            }
        ) {
            Text("Enable Frank")
        }

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                try {
                    if (!isRecording.value) {
                        audioRecorder.start()
                        isRecording.value = true
                        recordingStatus.value = "Recording..."
                    } else {
                        val file = audioRecorder.stop()
                        isRecording.value = false

                        if (file == null) {
                            recordingStatus.value =
                                "No recording created"
                            return@Button
                        }

                        recordingStatus.value =
                            "Sending to Frank..."

                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    webSocketClient.sendAudio(file)
                                }

                                recordingStatus.value =
                                    "Processing..."
                            } catch (exception: Exception) {
                                recordingStatus.value =
                                    exception.message
                                        ?: "Failed to send audio"
                            } finally {
                                file.delete()
                            }
                        }
                    }
                } catch (exception: Exception) {
                    isRecording.value = false
                    recordingStatus.value =
                        exception.message ?: "Recording failed"
                }
            }
        ) {
            Text(
                if (isRecording.value) {
                    "Stop recording"
                } else {
                    "Start recording"
                }
            )
        }


        Text(
            text = recordingStatus.value,
            modifier = Modifier.padding(top = 12.dp)
        )

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                coroutineScope.launch {
                    serverStatus.value = "Connecting..."

                    try {
                        val response = FrankClient.api.healthCheck()

                        serverStatus.value =
                            if (response.isSuccessful) {
                                "Frank server connected"
                            } else {
                                "Server returned HTTP ${response.code()}"
                            }
                    } catch (exception: Exception) {
                        serverStatus.value =
                            exception.message ?: "Connection failed"
                    }
                }
            }
        ) {
            Text("Test server connection")
        }

        Text(
            text = serverStatus.value,
            modifier = Modifier.padding(top = 12.dp)
        )

        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                webSocketClient.connect()
            }
        ) {
            Text("Connect device stream")
        }

        Text(
            text = streamStatus.value,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}