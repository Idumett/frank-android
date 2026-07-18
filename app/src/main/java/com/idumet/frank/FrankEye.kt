package com.idumet.frank

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun FrankEye(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val animation = rememberInfiniteTransition(
        label = "FrankEyeAnimation"
    )

    val primaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.Idle ->
                Color(0xFF38BDF8)

            AssistantState.Listening ->
                Color(0xFF22D3EE)

            AssistantState.Thinking ->
                Color(0xFFA855F7)

            AssistantState.Speaking ->
                Color(0xFF34D399)

            AssistantState.Error ->
                Color(0xFFEF4444)
        },
        animationSpec = tween(
            durationMillis = 500
        ),
        label = "FrankEyePrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.Idle ->
                Color(0xFF6366F1)

            AssistantState.Listening ->
                Color(0xFF0EA5E9)

            AssistantState.Thinking ->
                Color(0xFF6366F1)

            AssistantState.Speaking ->
                Color(0xFF06B6D4)

            AssistantState.Error ->
                Color(0xFFF97316)
        },
        animationSpec = tween(
            durationMillis = 500
        ),
        label = "FrankEyeSecondaryColor"
    )

    val irisStateScale by animateFloatAsState(
        targetValue = when (state) {
            AssistantState.Idle ->
                1f

            AssistantState.Listening ->
                1.05f

            AssistantState.Thinking ->
                0.88f

            AssistantState.Speaking ->
                1.12f

            AssistantState.Error ->
                0.94f
        },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutSlowInEasing
        ),
        label = "FrankIrisStateScale"
    )

    val eyeStateScale by animateFloatAsState(
        targetValue = when (state) {
            AssistantState.Thinking ->
                0.96f

            AssistantState.Speaking ->
                1.04f

            else ->
                1f
        },
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "FrankEyeStateScale"
    )

    val pulseTarget = when (state) {
        AssistantState.Idle ->
            1.03f

        AssistantState.Listening ->
            1.10f

        AssistantState.Thinking ->
            1.06f

        AssistantState.Speaking ->
            1.16f

        AssistantState.Error ->
            1.03f
    }

    val pulseDuration = when (state) {
        AssistantState.Idle ->
            1_800

        AssistantState.Listening ->
            900

        AssistantState.Thinking ->
            650

        AssistantState.Speaking ->
            450

        AssistantState.Error ->
            300
    }

    val pulse by animation.animateFloat(
        initialValue = 0.94f,
        targetValue = pulseTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankEyePulse"
    )

    val blink by animation.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4_200

                1f at 0
                1f at 3_500
                0.08f at 3_650
                1f at 3_800
                1f at 4_200
            }
        ),
        label = "FrankEyeBlink"
    )

    val pupilMovement by animation.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Listening ->
                        1_600

                    AssistantState.Thinking ->
                        900

                    AssistantState.Speaking ->
                        2_200

                    else ->
                        2_600
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankPupilMovement"
    )

    val glowRotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Thinking ->
                        1_700

                    AssistantState.Listening ->
                        3_500

                    AssistantState.Speaking ->
                        2_300

                    else ->
                        7_000
                }
            )
        ),
        label = "FrankGlowRotation"
    )

    Box(
        modifier = modifier.size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(230.dp)
        ) {
            val centre = Offset(
                x = size.width / 2f,
                y = size.height / 2f
            )

            val outerRadius =
                size.minDimension *
                        0.43f *
                        pulse *
                        eyeStateScale

            val glowBrush = Brush.sweepGradient(
                colors = listOf(
                    primaryColor,
                    secondaryColor,
                    primaryColor.copy(
                        alpha = 0.7f
                    ),
                    secondaryColor,
                    primaryColor
                ),
                center = centre
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(
                            alpha = 0.32f
                        ),
                        primaryColor.copy(
                            alpha = 0.08f
                        ),
                        Color.Transparent
                    ),
                    center = centre,
                    radius = outerRadius * 1.35f
                ),
                radius = outerRadius * 1.35f,
                center = centre
            )

            rotate(
                degrees = glowRotation,
                pivot = centre
            ) {
                drawCircle(
                    brush = glowBrush,
                    radius = outerRadius,
                    center = centre,
                    style = Stroke(
                        width =
                            size.minDimension *
                                    0.035f
                    ),
                    alpha = 0.7f
                )
            }

            val eyeWidth =
                size.width *
                        0.72f *
                        eyeStateScale

            val eyeHeight =
                size.height *
                        0.34f *
                        blink *
                        eyeStateScale

            val left =
                centre.x - eyeWidth / 2f

            val right =
                centre.x + eyeWidth / 2f

            val top =
                centre.y - eyeHeight / 2f

            val bottom =
                centre.y + eyeHeight / 2f

            val eyePath = Path().apply {
                moveTo(
                    left,
                    centre.y
                )

                cubicTo(
                    left + eyeWidth * 0.22f,
                    top,
                    right - eyeWidth * 0.22f,
                    top,
                    right,
                    centre.y
                )

                cubicTo(
                    right - eyeWidth * 0.22f,
                    bottom,
                    left + eyeWidth * 0.22f,
                    bottom,
                    left,
                    centre.y
                )

                close()
            }

            drawPath(
                path = eyePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(
                            alpha = 0.22f
                        ),
                        Color.White.copy(
                            alpha = 0.96f
                        ),
                        secondaryColor.copy(
                            alpha = 0.22f
                        )
                    )
                )
            )

            drawPath(
                path = eyePath,
                brush = glowBrush,
                style = Stroke(
                    width =
                        size.minDimension *
                                0.018f
                )
            )

            if (blink > 0.25f) {
                val movementMultiplier =
                    when (state) {
                        AssistantState.Listening ->
                            0.10f

                        AssistantState.Thinking ->
                            0.035f

                        AssistantState.Speaking ->
                            0.015f

                        else ->
                            0.07f
                    }

                val pupilOffset =
                    pupilMovement *
                            eyeWidth *
                            movementMultiplier

                val verticalMovement =
                    when (state) {
                        AssistantState.Speaking ->
                            0f

                        else ->
                            sin(pupilMovement) *
                                    eyeHeight *
                                    0.05f
                    }

                val irisCentre = Offset(
                    x = centre.x + pupilOffset,
                    y = centre.y +
                            verticalMovement
                )

                val irisRadius =
                    size.minDimension *
                            0.115f *
                            pulse *
                            irisStateScale

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor,
                            secondaryColor,
                            Color(0xFF020617)
                        ),
                        center = irisCentre,
                        radius = irisRadius
                    ),
                    radius = irisRadius,
                    center = irisCentre
                )

                drawCircle(
                    color = Color(0xFF020617),
                    radius = irisRadius * 0.42f,
                    center = irisCentre
                )

                drawCircle(
                    color = Color.White.copy(
                        alpha = 0.88f
                    ),
                    radius = irisRadius * 0.13f,
                    center = Offset(
                        x = irisCentre.x -
                                irisRadius * 0.25f,
                        y = irisCentre.y -
                                irisRadius * 0.28f
                    )
                )
            }
        }
    }
}