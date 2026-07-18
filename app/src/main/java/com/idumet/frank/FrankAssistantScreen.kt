package com.idumet.frank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FrankAssistantScreen(
    onClose: () -> Unit
) {
    var assistantState by remember {
        mutableStateOf(AssistantState.Idle)
    }

    val statusText = when (assistantState) {
        AssistantState.Idle -> "Ready"

        AssistantState.Listening -> "Listening..."

        AssistantState.Thinking -> "Thinking..."

        AssistantState.Speaking -> "Speaking..."

        AssistantState.Error -> "Something went wrong"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020617),
                        Color(0xFF0F172A),
                        Color(0xFF111827)
                    )
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FRANK",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 6.sp
        )

        FrankEye(
            state = assistantState,
            modifier = Modifier.padding(
                top = 32.dp,
                bottom = 24.dp
            )
        )

        Text(
            text = statusText,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp
        )

        FrankConversationHost(
            autoConnect = true,
            autoStartRecording = true,
            showConnectButton = false,
            showServerHealthControls = false,
            showCloseButton = true,
            onAssistantStateChanged = {
                assistantState = it
            },
            onClose = onClose
        )
    }
}