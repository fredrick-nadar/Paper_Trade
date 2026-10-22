package com.fredrick.papertrade

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val Gain = Color(0xFF1B7F3A)
private val Loss = Color(0xFFC62828)

private val ALL_RANGES = listOf("1M", "3M", "6M", "1Y", "3Y", "5Y", "10Y", "ALL")
private val RANGE_LABELS = mapOf("ALL" to "All")

data class PriceCandle(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double)
data class PriceHistory(val firstTradeDate: Long, val available: List<String>, val candles: List<PriceCandle>)

/** GET /stocks/{symbol}/history. `available` only lists ranges the stock has enough history for. */
suspend fun fetchHistory(token: String, symbol: String, range: String): PriceHistory {
    // Maps backend Yahoo/Finnhub-normalized candles into the chart model.
    val json = request("GET", "/stocks/$symbol/history?range=$range", token = token)
    val arr = json.getJSONArray("candles")
    val av = json.getJSONArray("available")
    return PriceHistory(
        json.getLong("firstTradeDate"),
        List(av.length()) { av.getString(it) },
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            PriceCandle(o.getLong("t"), o.getDouble("o"), o.getDouble("h"), o.getDouble("l"), o.getDouble("c"))
        }
    )
}

/** Price history for one stock: line or candlestick, with a range picker that adapts to the stock's age. */
@Composable
fun ChartCard(symbol: String, token: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Fetches history when the symbol/range changes and lets the user inspect line or candle views.
    var range by remember(symbol) { mutableStateOf("1M") }
    var candleMode by remember { mutableStateOf(false) }
    var history by remember(symbol) { mutableStateOf<PriceHistory?>(null) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }
    var selected by remember(symbol, range) { mutableStateOf<Int?>(null) }

    LaunchedEffect(symbol, range) {
        error = null
        try {
            val h = fetchHistory(token, symbol, range)
            // Stock too young for this range (e.g. switched to a recent IPO while on 10Y): fall back to All
            if (range !in h.available) { range = "ALL"; return@LaunchedEffect }
            history = h
        } catch (e: Exception) {
            error = if (e is AuthException) e.message else "Can't load chart"
        }
    }

    val h = history
    val ranges = h?.available ?: ALL_RANGES
    val candles = h?.candles.orEmpty()

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.55f)).padding(16.dp)) {
        // Header: price at the touched point (or the latest), and the move over the range
        val shown = selected?.let { candles.getOrNull(it) } ?: candles.lastOrNull()
        if (shown != null && candles.isNotEmpty()) {
            val change = shown.c - candles.first().c
            val pct = if (candles.first().c != 0.0) change / candles.first().c * 100 else 0.0
            val color = if (change >= 0) Gain else Loss
            val sign = if (change >= 0) "+" else "-"
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$${"%,.2f".format(shown.c)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextGreen, modifier = Modifier.weight(1f))
                Text("$sign${"%.2f".format(kotlin.math.abs(pct))}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(formatDate(shown.t, range), fontSize = 12.sp, color = SubtleText)
        } else {
            Text("Price history", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            when {
                error != null && candles.isEmpty() -> Text(error!!, color = Loss, fontSize = 13.sp)
                candles.isEmpty() -> CircularProgressIndicator(color = DarkGreen)
                else -> PriceChart(candles, candleMode, selected, onSelect = { selected = it })
            }
        }

        if (candles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(formatDate(candles.first().t, range), fontSize = 11.sp, color = SubtleText, modifier = Modifier.weight(1f))
                Text("L $${"%,.2f".format(candles.minOf { it.l })}  H $${"%,.2f".format(candles.maxOf { it.h })}", fontSize = 11.sp, color = SubtleText)
                Text(formatDate(candles.last().t, range), fontSize = 11.sp, color = SubtleText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }

        // Range picker: only the ranges this stock has history for
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ranges.forEach { r ->
                Chip(RANGE_LABELS[r] ?: r, r == range) {
                    if (r != range) Analytics.chartRangeChanged(context, symbol, r)
                    range = r
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Line", !candleMode) {
                if (candleMode) Analytics.chartTypeChanged(context, symbol, "Line")
                candleMode = false
            }
            Chip("Candles", candleMode) {
                if (!candleMode) Analytics.chartTypeChanged(context, symbol, "Candles")
                candleMode = true
            }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (active) Color.White else TextGreen,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) DarkGreen else Color.White.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun PriceChart(candles: List<PriceCandle>, candleMode: Boolean, selected: Int?, onSelect: (Int?) -> Unit) {
    val n = candles.size
    val lo = if (candleMode) candles.minOf { it.l } else candles.minOf { it.c }
    val hi = if (candleMode) candles.maxOf { it.h } else candles.maxOf { it.c }
    val pad = (hi - lo).takeIf { it > 0 }?.times(0.08) ?: (hi * 0.01)
    val min = lo - pad
    val span = (hi + pad) - min
    val up = candles.last().c >= candles.first().c

    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(n) {
                detectTapGestures(onTap = { onSelect(if (selected == null) indexAt(it.x, size.width, n) else null) })
            }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(indexAt(it.x, size.width, n)) },
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) },
                    onHorizontalDrag = { change, _ -> onSelect(indexAt(change.position.x, size.width, n)) }
                )
            }
    ) {
        fun x(i: Int) = (i + 0.5f) / n * size.width
        fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()

        if (candleMode) {
            val bodyW = (size.width / n * 0.7f).coerceIn(1.5f, 14f)
            candles.forEachIndexed { i, c ->
                val color = if (c.c >= c.o) Gain else Loss
                drawLine(color, Offset(x(i), y(c.h)), Offset(x(i), y(c.l)), strokeWidth = 1.5f)
                val top = y(maxOf(c.o, c.c))
                val bottom = y(minOf(c.o, c.c))
                drawRect(color, Offset(x(i) - bodyW / 2, top), Size(bodyW, (bottom - top).coerceAtLeast(1.5f)))
            }
        } else {
            val color = if (up) Gain else Loss
            val line = Path().apply {
                candles.forEachIndexed { i, c -> if (i == 0) moveTo(x(i), y(c.c)) else lineTo(x(i), y(c.c)) }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(n - 1), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        selected?.let { i ->
            drawLine(DarkGreen.copy(alpha = 0.6f), Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 2f)
            if (!candleMode) drawCircle(DarkGreen, 8f, Offset(x(i), y(candles[i].c)))
        }
    }
}

private fun indexAt(px: Float, width: Int, n: Int) = (px / width * n).toInt().coerceIn(0, n - 1)

// Short ranges show day + month; long ones show month + year
private fun formatDate(ms: Long, range: String): String {
    val pattern = if (range in listOf("1M", "3M", "6M")) "d MMM" else "MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}
