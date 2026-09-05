package com.idumet.frank

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationControls(
    isRecording: Boolean,
    isStreamReady: Boolean,
    recordingStatus: String,
    streamStatus: String,
    serverStatus: String,
    showConnectButton: Boolean,
    showServerHealthControls: Boolean,
    showCloseButton: Boolean,
    onToggleRecording: () -> Unit,
    onConnectStream: () -> Unit,
    onTestServer: () -> Unit,
    onClose: () -> Unit
) {
    val showDeveloperControls =
        showConnectButton ||
                showServerHealthControls

    if (showDeveloperControls) {
        if (showConnectButton) {
            Button(
                modifier = Modifier.padding(
                    top = 16.dp
                ),
                onClick = onConnectStream
            ) {
                Text("Connect device stream")
            }
        }

        Text(
            text = streamStatus,
            modifier = Modifier.padding(
                top = 12.dp
            )
        )

        Button(
            modifier = Modifier.padding(
                top = 16.dp
            ),
            enabled = isStreamReady,
            onClick = onToggleRecording
        ) {
            Text(
                text = if (isRecording) {
                    "Stop recording"
                } else {
                    "Start recording"
                }
            )
        }

        Text(
            text = recordingStatus,
            modifier = Modifier.padding(
                top = 12.dp
            )
        )

        if (showServerHealthControls) {
            Button(
                modifier = Modifier.padding(
                    top = 16.dp
                ),
                onClick = onTestServer
            ) {
                Text("Test server connection")
            }

            Text(
                text = serverStatus,
                modifier = Modifier.padding(
                    top = 12.dp
                )
            )
        }
    }

    if (showCloseButton) {
        TextButton(
            modifier = Modifier.padding(top = 30.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            onClick = onClose
        ) {
            Text(
                text = "Close",
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}
