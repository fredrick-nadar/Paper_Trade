package com.fredrick.papertrade

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val LAST_SCREEN = 7
private const val SCREEN_DURATION_MS = 4000L
private const val TOTAL_DOTS = 7        // Screens 2-8 only; the leaf screen has no dot

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {

    var currentScreen by remember { mutableIntStateOf(0) }

    // 0f -> 1f progress of the active dot
    val dotFill = remember { Animatable(0f) }

    // Moves to the next screen after 4 seconds
    LaunchedEffect(currentScreen) {
        if (currentScreen < LAST_SCREEN) {
            delay(SCREEN_DURATION_MS)
            currentScreen += 1
        }
    }

    // Restarts the dot fill every time the screen changes
    LaunchedEffect(currentScreen) {
        dotFill.snapTo(0f)
        val duration = if (currentScreen < LAST_SCREEN) SCREEN_DURATION_MS.toInt() else 600
        dotFill.animateTo(1f, tween(duration, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCFE8D2))
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
            label = "onboarding"
        ) { screen ->
            when (screen) {
                0 -> IntroScreen()
                1 -> WelcomeScreen()
                2 -> LearnScreen()
                3 -> VirtualMoneyScreen()
                4 -> ExploreStocksScreen()
                5 -> PracticeTradingScreen()
                6 -> PortfolioScreen()
                else -> FinalScreen(onGetStarted = onFinished)
            }
        }

        BottomStrand()

        // Hidden on the leaf screen (index 0), fades in from Screen 2 onwards
        AnimatedVisibility(
            visible = currentScreen > 0,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 64.dp)
        ) {
            PageIndicator(
                total = TOTAL_DOTS,
                current = (currentScreen - 1).coerceAtLeast(0),   // Screen 2 = first dot
                fill = dotFill.value
            )
        }
    }
}