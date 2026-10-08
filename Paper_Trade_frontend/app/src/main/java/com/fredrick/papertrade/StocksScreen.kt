package com.fredrick.papertrade

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Color(0xFFCFE8D2)
private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val Gain = Color(0xFF1B7F3A)
private val Loss = Color(0xFFC62828)

private const val REFRESH_MS = 30_000L   // matches the backend's 30-second cache

data class StockQuote(val symbol: String, val name: String, val logo: String, val price: Double, val change: Double, val changePercent: Double)

private suspend fun fetchStocks(token: String): Pair<List<StockQuote>, Long> {
    val json = request("GET", "/stocks", token = token)
    val arr = json.getJSONArray("stocks")
    val list = List(arr.length()) {
        val o = arr.getJSONObject(it)
        StockQuote(o.getString("symbol"), o.getString("name"), o.optString("logo"), o.getDouble("price"), o.getDouble("change"), o.getDouble("changePercent"))
    }
    return list to json.getLong("updatedAt")
}

@Composable
fun StocksScreen(user: User, token: String, onLogout: () -> Unit) {
    var stocks by remember { mutableStateOf<List<StockQuote>?>(null) }
    var updatedAt by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    // Loads prices now, then again every 30 seconds while this screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val (list, time) = fetchStocks(token)
                stocks = list
                updatedAt = time
                error = null
            } catch (e: AuthException) {
                if (e.code == 401) { onLogout(); return@LaunchedEffect }
                error = e.message
            } catch (e: Exception) {
                error = "Can't reach the server"
            }
            delay(REFRESH_MS)
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            // Header: greeting + balance, log out on the right
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hi, ${user.name}", fontSize = 15.sp, color = SubtleText)
                    Text(
                        "$${"%,.2f".format(user.balance)}",
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextGreen
                    )
                    Text("Available balance", fontSize = 13.sp, color = SubtleText)
                }
                TextButton(onClick = onLogout) { Text("Log out", color = DarkGreen) }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Stocks", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextGreen, modifier = Modifier.weight(1f))
                if (updatedAt > 0) {
                    Text(
                        "Updated " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAt)),
                        fontSize = 12.sp, color = SubtleText
                    )
                }
            }
            error?.let { Text(it, color = Loss, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))

            val list = stocks
            if (list == null) {
                if (error == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(color = DarkGreen)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    items(list, key = { it.symbol }) { StockRow(it) }
                }
            }
        }
    }
}

@Composable
private fun StockRow(stock: StockQuote) {
    val color = if (stock.change >= 0) Gain else Loss
    val sign = if (stock.change >= 0) "+" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Company logo from Finnhub (transparent, sits on the card); initials in a green circle if it is missing or fails to load
        val initials: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(DarkGreen), contentAlignment = Alignment.Center) {
                Text(stock.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        var logoFailed by remember { mutableStateOf(false) }
        Box(Modifier.size(42.dp)) {
            if (stock.logo.isBlank() || logoFailed) Box(Modifier.fillMaxSize().clip(CircleShape)) { initials() }
            else AsyncImage(
                model = stock.logo,
                contentDescription = stock.name,
                contentScale = ContentScale.Crop,
                onError = { logoFailed = true },
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stock.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text(stock.name, fontSize = 13.sp, color = SubtleText, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$${"%,.2f".format(stock.price)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text(
                "$sign${"%.2f".format(kotlin.math.abs(stock.change))} ($sign${"%.2f".format(kotlin.math.abs(stock.changePercent))}%)",
                fontSize = 13.sp, color = color
            )
        }
    }
}
