package com.fredrick.papertrade

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FillColor = Color(0xFF315D3C)

/**
 * Row of dots, one per scene.
 * [current]  = index of the active scene (0-based)
 * [fill]     = 0f..1f, how much of the ACTIVE dot is filled
 */
@Composable
fun PageIndicator(
    total: Int,
    current: Int,
    fill: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->

            // The active dot grows into a pill; the others shrink back to a circle.
            // animateDpAsState smoothly animates whenever the target changes.
            val width by animateDpAsState(
                targetValue = if (index == current) 28.dp else 8.dp,
                animationSpec = tween(400),
                label = "dotWidth"
            )

            // Past dots: full. Active dot: partly filled. Future dots: empty.
            val fraction = when {
                index < current -> 1f
                index == current -> fill
                else -> 0f
            }

            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(FillColor.copy(alpha = 0.25f))   // empty track
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)                  // the filled part
                        .background(FillColor)
                )
            }
        }
    }
}