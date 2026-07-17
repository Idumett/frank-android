package com.idumet.frank

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationControls(
    isRecording: Boolean,
    recordingStatus: String,
    streamStatus: String,
    serverStatus: String,
    showServerHealthControls: Boolean,
    showCloseButton: Boolean,
    onToggleRecording: () -> Unit,
    onConnectStream: () -> Unit,
    onTestServer: () -> Unit,
    onClose: () -> Unit
) {
    Button(
        modifier = Modifier.padding(top = 16.dp),
        onClick = onConnectStream
    ) {
        Text("Connect device stream")
    }

    Text(
        text = streamStatus,
        modifier = Modifier.padding(top = 12.dp)
    )

    Button(
        modifier = Modifier.padding(top = 16.dp),
        onClick = onToggleRecording
    ) {
        Text(
            if (isRecording) {
                "Stop recording"
            } else {
                "Start recording"
            }
        )
    }

    Text(
        text = recordingStatus,
        modifier = Modifier.padding(top = 12.dp)
    )

    if (showServerHealthControls) {
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = onTestServer
        ) {
            Text("Test server connection")
        }

        Text(
            text = serverStatus,
            modifier = Modifier.padding(top = 12.dp)
        )
    }

    if (showCloseButton) {
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = onClose
        ) {
            Text("Close")
        }
    }
}