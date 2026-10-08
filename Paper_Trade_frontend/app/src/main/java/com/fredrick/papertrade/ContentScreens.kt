package com.fredrick.papertrade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

// ---------- Shared colours ----------
private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val Gain = Color(0xFF1B7F3A)
private val Loss = Color(0xFFC62828)


// =====================================================
// Reusable building blocks
// =====================================================

/** Fades and scales its content in once, when it first appears. */
@Composable
fun FadeScaleIn(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700)) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress.value
                scaleX = 0.92f + 0.08f * progress.value
                scaleY = 0.92f + 0.08f * progress.value
            }
    ) { content() }
}

/**
 * Layout used by every content screen: title, supporting text, then an illustration.
 * [highlight] is an optional part of the title that gets a different colour.
 */
@Composable
fun ScreenLayout(
    title: String,
    subtitle: String,
    highlight: String? = null,
    illustration: @Composable () -> Unit = {}
) {
    // Build a title where only the highlighted part is styled differently
    val styledTitle = buildAnnotatedString {
        val start = if (highlight != null) title.indexOf(highlight) else -1
        if (highlight == null || start < 0) {
            append(title)
        } else {
            append(title.substring(0, start))
            withStyle(SpanStyle(color = Gain, fontWeight = FontWeight.ExtraBold)) {
                append(highlight)
            }
            append(title.substring(start + highlight.length))
        }
    }

    FadeScaleIn {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 130.dp),     // keeps content clear of the bottom strand
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = styledTitle,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextGreen,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = SubtleText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            illustration()
        }
    }
}

/** A soft white rounded card used behind illustrations. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) { content() }
}


// =====================================================
// Screen 2: Welcome
// =====================================================
@Composable
fun WelcomeScreen() {
    ScreenLayout(
        title = "Paper Trade",
        subtitle = "Practice trading without real money."
    ) {
        SoftCard {
            MarketChart(Modifier.fillMaxWidth().height(130.dp))
        }
    }
}

/** A line chart that draws itself from left to right. */
@Composable
fun MarketChart(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1800, delayMillis = 300))
    }
    val points = listOf(0.8f, 0.6f, 0.68f, 0.45f, 0.52f, 0.3f, 0.38f, 0.15f)

    Canvas(modifier) {
        // faint grid lines
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(DarkGreen.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 2f)
        }

        // full line
        val stepX = size.width / (points.size - 1)
        val line = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * v
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // PathMeasure lets us draw only the first part of the path
        val measure = PathMeasure()
        measure.setPath(line, false)
        val visible = Path()
        measure.getSegment(0f, measure.length * progress.value, visible, true)

        drawPath(
            visible, Gain,
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}


// =====================================================
// Screen 3: Learn
// =====================================================
@Composable
fun LearnScreen() {
    ScreenLayout(
        title = "Learn by Trading",
        subtitle = "Explore stocks and understand how trades affect your portfolio."
    ) {
        SoftCard {
            CandleChart(Modifier.fillMaxWidth().height(130.dp))
        }
    }
}

private data class Candle(val low: Float, val high: Float, val open: Float, val close: Float)

/** A simple candlestick chart. Values are 0..1 (1 = highest price). */
@Composable
fun CandleChart(modifier: Modifier = Modifier) {
    val candles = listOf(
        Candle(0.25f, 0.45f, 0.30f, 0.40f),
        Candle(0.30f, 0.55f, 0.40f, 0.50f),
        Candle(0.35f, 0.55f, 0.50f, 0.38f),
        Candle(0.30f, 0.50f, 0.38f, 0.46f),
        Candle(0.40f, 0.70f, 0.46f, 0.65f),
        Candle(0.50f, 0.80f, 0.65f, 0.74f),
        Candle(0.55f, 0.90f, 0.74f, 0.85f)
    )

    Canvas(modifier) {
        val slot = size.width / candles.size
        fun y(v: Float) = size.height * (1f - v)   // flips so higher price = higher on screen

        candles.forEachIndexed { i, c ->
            val cx = slot * i + slot / 2f
            val color = if (c.close >= c.open) Gain else Loss

            // wick (thin line from low to high)
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), strokeWidth = 4f)

            // body (rectangle from open to close)
            val top = y(max(c.open, c.close))
            val bottom = y(min(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(cx - slot * 0.25f, top),
                size = Size(slot * 0.5f, (bottom - top).coerceAtLeast(4f))
            )
        }
    }
}


// =====================================================
// Screen 4: Virtual Money
// =====================================================
@Composable
fun VirtualMoneyScreen() {
    ScreenLayout(
        title = "Start With \$1,000",
        highlight = "\$1,000",
        subtitle = "You get virtual money to practice buying and selling stocks."
    ) {
        MoneyBadge()
    }
}

/** A badge whose number counts up from 0 to 1,000. */
@Composable
fun MoneyBadge() {
    val amount = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        amount.animateTo(1000f, tween(durationMillis = 1500, delayMillis = 400))
    }

    Box(
        modifier = Modifier
            .background(DarkGreen, RoundedCornerShape(24.dp))
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Text(
            text = "\$" + "%,d".format(amount.value.toInt()),
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}


// =====================================================
// Screen 5: Explore Stocks (static demo data)
// =====================================================
private data class Stock(
    val symbol: String,
    val name: String,
    val price: String,
    val change: String,
    val isUp: Boolean
)

private val demoStocks = listOf(
    Stock("AAPL", "Apple Inc.", "\$189.40", "+1.24%", true),
    Stock("TSLA", "Tesla Inc.", "\$242.18", "-0.87%", false),
    Stock("MSFT", "Microsoft Corp.", "\$415.60", "+0.52%", true)
)

@Composable
fun ExploreStocksScreen() {
    ScreenLayout(
        title = "Explore Stocks",
        subtitle = "Discover stocks, view prices, and learn about market movements."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            demoStocks.forEach { StockCard(it) }
        }
    }
}

@Composable
private fun StockCard(stock: Stock) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stock.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text(stock.name, fontSize = 13.sp, color = SubtleText)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stock.price, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextGreen)
            Text(
                stock.change,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (stock.isUp) Gain else Loss
            )
        }
    }
}


// =====================================================
// Screen 6: Practice Trading (illustration only)
// =====================================================
@Composable
fun PracticeTradingScreen() {
    ScreenLayout(
        title = "Trade Without Risk",
        subtitle = "Buy and sell using virtual money while you learn."
    ) {
        TradeIllustration()
    }
}

@Composable
private fun TradeIllustration() {
    // An infinite animation that gently pulses the Buy button
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "buyScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AAPL", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextGreen,
                modifier = Modifier.weight(1f))
            Text("\$189.40", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextGreen)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Quantity", fontSize = 14.sp, color = SubtleText, modifier = Modifier.weight(1f))
            Text("5 shares", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextGreen)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TradeButton(
                label = "Buy",
                color = Gain,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
            TradeButton(label = "Sell", color = Loss, modifier = Modifier.weight(1f))
        }
    }
}

/** Looks like a button but does nothing: this screen is only an illustration. */
@Composable
private fun TradeButton(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}


// =====================================================
// Screen 7: Track Portfolio (static demo data)
// =====================================================
@Composable
fun PortfolioScreen() {
    ScreenLayout(
        title = "Track Your Progress",
        subtitle = "See your holdings, available cash, and profit or loss in one place."
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Total value", fontSize = 13.sp, color = SubtleText)
            Text("\$1,084.50", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextGreen)
            Text("+\$84.50 (+8.45%)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gain)

            Spacer(Modifier.height(16.dp))
            InfoRow("Available cash", "\$479.50")
            Spacer(Modifier.height(8.dp))
            InfoRow("AAPL · 1 share", "\$189.40")
            Spacer(Modifier.height(8.dp))
            InfoRow("MSFT · 1 share", "\$415.60")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = SubtleText, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextGreen)
    }
}


// =====================================================
// Screen 8: Final CTA (no auto-advance)
// =====================================================
@Composable
fun FinalScreen(onGetStarted: () -> Unit) {
    ScreenLayout(
        title = "Ready to start?",
        subtitle = "Build your trading skills without risking real money."
    ) {
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkGreen,
                contentColor = Color.White
            )
        ) {
            Text("Let's Get Started →", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}