package com.fredrick.papertrade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IntroScreen() {
    // Decorative opening scene; it has no authentication or trading side effects.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // Each leaf: different start position, size, speed, sway and delay.
        // All finish before the 4-second mark.
        FallingLeaf(
            size = 72.dp, startX = (-110).dp, screenHeightPx = screenHeightPx,
            durationMillis = 2800, startDelay = 0,
            swings = 2, swingAmplitude = 28.dp, maxTilt = 25f, phaseOffset = 0f
        )
        FallingLeaf(
            size = 44.dp, startX = 20.dp, screenHeightPx = screenHeightPx,
            durationMillis = 3200, startDelay = 250,
            swings = 3, swingAmplitude = 36.dp, maxTilt = 35f, phaseOffset = 1.5f
        )
        FallingLeaf(
            size = 54.dp, startX = 115.dp, screenHeightPx = screenHeightPx,
            durationMillis = 3500, startDelay = 450,
            swings = 2, swingAmplitude = 24.dp, maxTilt = 28f, phaseOffset = 3f
        )
    }
}

@Composable
fun BoxScope.FallingLeaf(
    size: Dp,
    startX: Dp,
    screenHeightPx: Float,
    durationMillis: Int,
    startDelay: Long,
    swings: Int,
    swingAmplitude: Dp,
    maxTilt: Float,
    phaseOffset: Float,
    fadeStart: Float = 0.8f
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val startXPx = with(density) { startX.toPx() }
    val amplitudePx = with(density) { swingAmplitude.toPx() }

    // progress goes 0f -> 1f. Everything else is calculated from it.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(startDelay)
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    }

    Image(
        painter = painterResource(R.drawable.leaf),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .size(size)
            .graphicsLayer {
                val p = progress.value
                val phase = p * swings * 2f * PI.toFloat() + phaseOffset

                translationX = startXPx + sin(phase) * amplitudePx
                translationY = -sizePx + (screenHeightPx) * p
                rotationZ = cos(phase) * maxTilt
                alpha = if (p < fadeStart) 1f else 1f - (p - fadeStart) / (1f - fadeStart)
            }
    )
}

@Composable
fun BottomStrand() {
    // Shared decorative curve used below onboarding/auth content.
    Canvas(Modifier.fillMaxSize()) {
        val path = Path()
        path.moveTo(0f, size.height - 90f)
        path.cubicTo(
            size.width * 0.20f, size.height - 150f,
            size.width * 0.35f, size.height - 30f,
            size.width * 0.55f, size.height - 100f
        )
        path.cubicTo(
            size.width * 0.72f, size.height - 160f,
            size.width * 0.85f, size.height - 40f,
            size.width, size.height - 110f
        )
        drawPath(
            path = path,
            color = Color(0xFF315D3C),
            style = Stroke(width = 7f, cap = StrokeCap.Round)
        )
    }
}