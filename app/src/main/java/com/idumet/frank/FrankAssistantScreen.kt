package com.idumet.frank

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    var isVisible by remember {
        mutableStateOf(false)
    }

    val backgroundAnimation = rememberInfiniteTransition(
        label = "FrankBackgroundAnimation"
    )

    val gradientMovement by backgroundAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 9_000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankBackgroundMovement"
    )

    val statusText = when (assistantState) {
        AssistantState.Idle ->
            "Ready"

        AssistantState.Listening ->
            "Listening..."

        AssistantState.Thinking ->
            "Thinking..."

        AssistantState.Speaking ->
            "Speaking..."

        AssistantState.Error ->
            "Sorry, I didn't catch that."
    }

    val backgroundColors = listOf(
        Color(
            red = 0.01f + gradientMovement * 0.01f,
            green = 0.03f,
            blue = 0.09f + gradientMovement * 0.03f
        ),
        Color(
            red = 0.05f,
            green = 0.09f + gradientMovement * 0.02f,
            blue = 0.17f
        ),
        Color(
            red = 0.07f + gradientMovement * 0.02f,
            green = 0.09f,
            blue = 0.15f
        )
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = backgroundColors
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 350
                )
            ) + scaleIn(
                initialScale = 0.82f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 320f
                )
            )
        ) {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.Center
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

                Crossfade(
                    targetState = statusText,
                    animationSpec = tween(
                        durationMillis = 250
                    ),
                    label = "FrankStatusTransition"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White.copy(
                            alpha = 0.85f
                        ),
                        fontSize = 18.sp
                    )
                }

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
    }
}