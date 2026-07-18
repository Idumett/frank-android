package com.idumet.frank

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FrankAssistantScreen(
    onClose: () -> Unit
) {
    var assistantState by remember {
        mutableStateOf(AssistantState.Idle)
    }

    var contentVisible by remember {
        mutableStateOf(false)
    }

    val backgroundAnimation = rememberInfiniteTransition(
        label = "FrankBackgroundAnimation"
    )

    val backgroundMovement by backgroundAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 10_000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankBackgroundMovement"
    )

    val backgroundGlow by backgroundAnimation.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 4_800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankBackgroundGlow"
    )

    val statusText = when (assistantState) {
        AssistantState.Idle ->
            "READY"

        AssistantState.Listening ->
            "LISTENING"

        AssistantState.Thinking ->
            "THINKING"

        AssistantState.Speaking ->
            "SPEAKING"

        AssistantState.Error ->
            "PLEASE TRY AGAIN"
    }

    val statusSubtitle = when (assistantState) {
        AssistantState.Idle ->
            "Frank is ready"

        AssistantState.Listening ->
            "Speak naturally"

        AssistantState.Thinking ->
            "Understanding your request"

        AssistantState.Speaking ->
            "Frank is responding"

        AssistantState.Error ->
            "Something interrupted the request"
    }

    val stateGlowColor = when (assistantState) {
        AssistantState.Idle ->
            Color(0xFF2563EB)

        AssistantState.Listening ->
            Color(0xFF06B6D4)

        AssistantState.Thinking ->
            Color(0xFF8B5CF6)

        AssistantState.Speaking ->
            Color(0xFF10B981)

        AssistantState.Error ->
            Color(0xFFEF4444)
    }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF010409),
                        Color(0xFF06101F),
                        Color(0xFF020617),
                        Color(0xFF00030A)
                    ),
                    start = Offset(
                        x = backgroundMovement * 900f,
                        y = 0f
                    ),
                    end = Offset(
                        x = 900f - backgroundMovement * 500f,
                        y = 1_900f
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            stateGlowColor.copy(
                                alpha = 0.13f * backgroundGlow
                            ),
                            stateGlowColor.copy(
                                alpha = 0.045f
                            ),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = 540f + backgroundMovement * 80f,
                            y = 850f - backgroundMovement * 70f
                        ),
                        radius = 1_000f
                    )
                )
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(
                animationSpec = tween(550)
            ) + scaleIn(
                initialScale = 0.72f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 260f
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 24.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FrankEye(
                    state = assistantState
                )

                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = {
                        (
                                fadeIn(
                                    animationSpec = tween(260)
                                ) + slideInVertically {
                                    it / 3
                                }
                                ).togetherWith(
                                fadeOut(
                                    animationSpec = tween(180)
                                ) + slideOutVertically {
                                    -it / 3
                                }
                            ).using(
                                SizeTransform(
                                    clip = false
                                )
                            )
                    },
                    label = "FrankStatusTransition"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White.copy(
                            alpha = 0.94f
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedContent(
                    targetState = statusSubtitle,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(320)
                        ).togetherWith(
                            fadeOut(
                                animationSpec = tween(180)
                            )
                        )
                    },
                    label = "FrankSubtitleTransition"
                ) { text ->
                    Text(
                        text = text,
                        modifier = Modifier.padding(
                            top = 10.dp
                        ),
                        color = Color.White.copy(
                            alpha = 0.46f
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Center
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