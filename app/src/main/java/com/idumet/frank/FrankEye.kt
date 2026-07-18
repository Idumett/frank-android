package com.idumet.frank

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class FrankParticle(
    val angle: Float,
    val radiusFactor: Float,
    val sizeFactor: Float,
    val speedFactor: Float,
    val alpha: Float
)

@Composable
fun FrankEye(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val animation = rememberInfiniteTransition(
        label = "FrankCoreAnimation"
    )

    val particles = remember {
        val random = Random(42L)

        List(28) {
            FrankParticle(
                angle = random.nextFloat() * 360f,
                radiusFactor =
                    0.62f + random.nextFloat() * 0.42f,
                sizeFactor =
                    0.45f + random.nextFloat() * 1.4f,
                speedFactor =
                    0.5f + random.nextFloat() * 1.4f,
                alpha =
                    0.25f + random.nextFloat() * 0.65f
            )
        }
    }

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
        animationSpec = tween(500),
        label = "FrankCorePrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.Idle ->
                Color(0xFF2563EB)

            AssistantState.Listening ->
                Color(0xFF0284C7)

            AssistantState.Thinking ->
                Color(0xFF6366F1)

            AssistantState.Speaking ->
                Color(0xFF06B6D4)

            AssistantState.Error ->
                Color(0xFFF97316)
        },
        animationSpec = tween(500),
        label = "FrankCoreSecondaryColor"
    )

    val stateScale by animateFloatAsState(
        targetValue = when (state) {
            AssistantState.Idle ->
                1f

            AssistantState.Listening ->
                1.035f

            AssistantState.Thinking ->
                0.98f

            AssistantState.Speaking ->
                1.075f

            AssistantState.Error ->
                0.97f
        },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutSlowInEasing
        ),
        label = "FrankCoreStateScale"
    )

    val coreScale by animateFloatAsState(
        targetValue = when (state) {
            AssistantState.Thinking ->
                0.84f

            AssistantState.Speaking ->
                1.13f

            AssistantState.Listening ->
                1.04f

            else ->
                1f
        },
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing
        ),
        label = "FrankInnerCoreScale"
    )

    val breathingPulse by animation.animateFloat(
        initialValue = 0.94f,
        targetValue = when (state) {
            AssistantState.Idle ->
                1.03f

            AssistantState.Listening ->
                1.08f

            AssistantState.Thinking ->
                1.025f

            AssistantState.Speaking ->
                1.14f

            AssistantState.Error ->
                1.04f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Idle ->
                        2_400

                    AssistantState.Listening ->
                        900

                    AssistantState.Thinking ->
                        1_250

                    AssistantState.Speaking ->
                        270

                    AssistantState.Error ->
                        360
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FrankCoreBreathingPulse"
    )

    val mainRotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Idle ->
                        14_000

                    AssistantState.Listening ->
                        6_000

                    AssistantState.Thinking ->
                        2_200

                    AssistantState.Speaking ->
                        3_400

                    AssistantState.Error ->
                        1_400
                },
                easing = LinearEasing
            )
        ),
        label = "FrankMainRotation"
    )

    val reverseRotation by animation.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Thinking ->
                        3_000

                    AssistantState.Listening ->
                        7_500

                    AssistantState.Speaking ->
                        4_200

                    else ->
                        18_000
                },
                easing = LinearEasing
            )
        ),
        label = "FrankReverseRotation"
    )

    val particleRotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Thinking ->
                        5_000

                    AssistantState.Listening ->
                        9_000

                    AssistantState.Speaking ->
                        6_500

                    else ->
                        18_000
                },
                easing = LinearEasing
            )
        ),
        label = "FrankParticleRotation"
    )

    val scannerRotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.Thinking ->
                        1_300

                    AssistantState.Listening ->
                        2_800

                    AssistantState.Speaking ->
                        1_900

                    else ->
                        5_500
                },
                easing = LinearEasing
            )
        ),
        label = "FrankScannerRotation"
    )

    Box(
        modifier = modifier.size(340.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(340.dp)
        ) {
            val centre = Offset(
                x = size.width / 2f,
                y = size.height / 2f
            )

            val minimumDimension = size.minDimension
            val baseRadius =
                minimumDimension * 0.31f * stateScale

            val outerGlowRadius =
                baseRadius * 1.55f * breathingPulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f),
                        secondaryColor.copy(alpha = 0.10f),
                        primaryColor.copy(alpha = 0.025f),
                        Color.Transparent
                    ),
                    center = centre,
                    radius = outerGlowRadius
                ),
                radius = outerGlowRadius,
                center = centre,
                blendMode = BlendMode.Screen
            )

            particles.forEachIndexed {
                    index,
                    particle ->

                val stateSpeed =
                    when (state) {
                        AssistantState.Thinking ->
                            1.8f

                        AssistantState.Listening ->
                            1.25f

                        AssistantState.Speaking ->
                            1.55f

                        else ->
                            0.8f
                    }

                val angle =
                    (
                            particle.angle +
                                    particleRotation *
                                    particle.speedFactor *
                                    stateSpeed
                            ) * PI.toFloat() / 180f

                val orbitRadius =
                    baseRadius *
                            particle.radiusFactor *
                            1.65f

                val particleCentre = Offset(
                    x = centre.x +
                            cos(angle) * orbitRadius,
                    y = centre.y +
                            sin(angle) * orbitRadius
                )

                val flicker =
                    0.65f +
                            0.35f *
                            sin(
                                particleRotation *
                                        0.04f +
                                        index
                            )

                drawCircle(
                    color = primaryColor.copy(
                        alpha = (
                                particle.alpha *
                                        flicker
                                ).coerceIn(
                                0.08f,
                                0.85f
                            )
                    ),
                    radius =
                        minimumDimension *
                                0.0045f *
                                particle.sizeFactor,
                    center = particleCentre
                )
            }

            rotate(
                degrees = mainRotation,
                pivot = centre
            ) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor,
                            secondaryColor,
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.65f),
                            Color.Transparent
                        ),
                        center = centre
                    ),
                    startAngle = 8f,
                    sweepAngle = 322f,
                    useCenter = false,
                    topLeft = Offset(
                        centre.x - baseRadius * 1.26f,
                        centre.y - baseRadius * 1.26f
                    ),
                    size = Size(
                        width = baseRadius * 2.52f,
                        height = baseRadius * 2.52f
                    ),
                    style = Stroke(
                        width = minimumDimension * 0.017f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(
                                minimumDimension * 0.075f,
                                minimumDimension * 0.025f
                            )
                        )
                    )
                )
            }

            rotate(
                degrees = reverseRotation,
                pivot = centre
            ) {
                drawArc(
                    color = secondaryColor.copy(
                        alpha = 0.54f
                    ),
                    startAngle = 28f,
                    sweepAngle = 278f,
                    useCenter = false,
                    topLeft = Offset(
                        centre.x - baseRadius * 1.08f,
                        centre.y - baseRadius * 1.08f
                    ),
                    size = Size(
                        width = baseRadius * 2.16f,
                        height = baseRadius * 2.16f
                    ),
                    style = Stroke(
                        width = minimumDimension * 0.009f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(
                                minimumDimension * 0.025f,
                                minimumDimension * 0.022f
                            )
                        )
                    )
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.42f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color(0xFF020617).copy(alpha = 0.82f)
                    ),
                    center = centre,
                    radius =
                        baseRadius *
                                0.82f *
                                breathingPulse
                ),
                radius =
                    baseRadius *
                            0.82f *
                            breathingPulse,
                center = centre
            )

            drawCircle(
                color = Color(0xFF020617).copy(
                    alpha = 0.82f
                ),
                radius = baseRadius * 0.66f,
                center = centre
            )

            rotate(
                degrees = scannerRotation,
                pivot = centre
            ) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.9f),
                            primaryColor,
                            Color.Transparent
                        ),
                        center = centre
                    ),
                    startAngle = 0f,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = Offset(
                        centre.x - baseRadius * 0.70f,
                        centre.y - baseRadius * 0.70f
                    ),
                    size = Size(
                        width = baseRadius * 1.40f,
                        height = baseRadius * 1.40f
                    ),
                    style = Stroke(
                        width = minimumDimension * 0.011f,
                        cap = StrokeCap.Round
                    )
                )
            }

            val innerRadius =
                baseRadius *
                        0.39f *
                        coreScale *
                        breathingPulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        primaryColor,
                        secondaryColor.copy(alpha = 0.82f),
                        Color(0xFF020617)
                    ),
                    center = Offset(
                        x = centre.x - innerRadius * 0.18f,
                        y = centre.y - innerRadius * 0.20f
                    ),
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = centre
            )

            drawCircle(
                color = Color(0xFF01030A),
                radius = innerRadius * 0.43f,
                center = centre
            )

            drawCircle(
                color = Color.White.copy(
                    alpha = 0.93f
                ),
                radius = innerRadius * 0.12f,
                center = Offset(
                    x = centre.x - innerRadius * 0.22f,
                    y = centre.y - innerRadius * 0.25f
                )
            )

            drawCircle(
                color = primaryColor.copy(
                    alpha = 0.52f
                ),
                radius = innerRadius * 0.08f,
                center = Offset(
                    x = centre.x + innerRadius * 0.28f,
                    y = centre.y + innerRadius * 0.22f
                )
            )
        }
    }
}