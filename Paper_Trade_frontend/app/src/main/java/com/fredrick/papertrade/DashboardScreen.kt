package com.fredrick.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Background = Color(0xFFCFE8D2)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val Gain = Color(0xFF1B7F3A)
private val Loss = Color(0xFFC62828)
private val Card = Color.White.copy(alpha = 0.55f)

private data class Position(
    val symbol: String, val name: String, val logo: String, val quantity: Int,
    val avgPrice: Double, val price: Double, val value: Double, val pnl: Double, val pnlPercent: Double
)

private data class Portfolio(
    val cash: Double, val invested: Double, val value: Double, val pnl: Double, val pnlPercent: Double,
    val holdings: List<Position>
)

private suspend fun fetchPortfolio(token: String): Portfolio {
    // Maps GET /portfolio into a UI model containing summary values and holding rows.
    val j = request("GET", "/portfolio", token = token)
    val arr = j.getJSONArray("holdings")
    val holdings = List(arr.length()) {
        val h = arr.getJSONObject(it)
        Position(
            h.getString("symbol"), h.getString("name"), h.optString("logo"), h.getInt("quantity"),
            h.getDouble("avgPrice"), h.getDouble("price"), h.getDouble("value"), h.getDouble("pnl"), h.getDouble("pnlPercent")
        )
    }
    return Portfolio(j.getDouble("cash"), j.getDouble("invested"), j.getDouble("value"), j.getDouble("pnl"), j.getDouble("pnlPercent"), holdings)
}

private fun money(v: Double) = "$${"%,.2f".format(v)}"
private fun signed(v: Double) = (if (v >= 0) "+" else "-") + money(abs(v))
private fun signedPct(v: Double) = (if (v >= 0) "+" else "-") + "%.2f".format(abs(v)) + "%"

@Composable
fun DashboardScreen(token: String, onLogout: () -> Unit) {
    // The dashboard refreshes independently so live prices and P/L remain current.
    var portfolio by remember { mutableStateOf<Portfolio?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Loads on open, then refreshes every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val loaded = fetchPortfolio(token)
                // Track once per screen visit, not on every 30-second refresh.
                if (portfolio == null) Analytics.portfolioViewed(context, loaded.holdings.size, loaded.value, loaded.pnl)
                portfolio = loaded
                error = null
            } catch (e: AuthException) {
                if (e.code == 401) { onLogout(); return@LaunchedEffect }
                error = e.message
            } catch (e: Exception) {
                error = "Can't reach the server"
            }
            delay(30_000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Dashboard", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        error?.let { Text(it, color = Loss, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(14.dp))

        val p = portfolio
        if (p == null) {
            if (error == null) Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(color = Color(0xFF315D3C))
            }
            return@Column
        }

        // Summary: portfolio value, profit/loss, then cash and invested
        val pnlColor = if (p.pnl >= 0) Gain else Loss
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
            Text("Portfolio value", fontSize = 13.sp, color = SubtleText)
            Text(money(p.value), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text("${signed(p.pnl)} (${signedPct(p.pnlPercent)})", fontSize = 15.sp, color = pnlColor)
            Spacer(Modifier.height(4.dp))
            Text("Profit / Loss", fontSize = 12.sp, color = SubtleText)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            StatCard("Available cash", money(p.cash), Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StatCard("Invested", money(p.invested), Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        Text("Holdings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        Spacer(Modifier.height(10.dp))

        if (p.holdings.isEmpty()) {
            Text("No holdings yet. Buy a stock from the Trade tab.", fontSize = 14.sp, color = SubtleText)
        }
        p.holdings.forEach {
            HoldingRow(it)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
        Text(label, fontSize = 12.sp, color = SubtleText)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextGreen)
    }
}

@Composable
private fun HoldingRow(h: Position) {
    val color = if (h.pnl >= 0) Gain else Loss
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompanyLogo(h.symbol, h.name, h.logo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text("${h.quantity} ${if (h.quantity == 1) "share" else "shares"} · avg ${money(h.avgPrice)}", fontSize = 13.sp, color = SubtleText)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(h.value), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text("${signed(h.pnl)} (${signedPct(h.pnlPercent)})", fontSize = 13.sp, color = color)
        }
    }
}
