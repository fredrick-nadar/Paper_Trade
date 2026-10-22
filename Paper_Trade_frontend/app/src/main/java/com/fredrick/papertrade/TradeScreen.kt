package com.fredrick.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

private val Background = Color(0xFFCFE8D2)
private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val Gain = Color(0xFF1B7F3A)
private val Loss = Color(0xFFC62828)

private fun money(v: Double) = "$${"%,.2f".format(v)}"

/** Shares owned per symbol, from GET /holdings. */
private suspend fun fetchOwned(token: String): Map<String, Int> {
    // Loads quantities used for the sell-side UI hint; the server remains authoritative.
    val arr = request("GET", "/holdings", token = token).getJSONArray("holdings")
    return (0 until arr.length()).associate { arr.getJSONObject(it).let { h -> h.getString("symbol") to h.getInt("quantity") } }
}

@Composable
fun TradeScreen(
    user: User,
    token: String,
    initialSymbol: String?,
    onBalanceChange: (Double) -> Unit,
    onLogout: () -> Unit
) {
    // Trade state is local to this screen; successful orders update balance and owned quantities.
    var stocks by remember { mutableStateOf<List<StockQuote>?>(null) }
    var owned by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var symbol by remember { mutableStateOf(initialSymbol) }
    var isBuy by remember { mutableStateOf(true) }
    var qty by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }   // (isSuccess, text)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Hold back in-app campaigns while an order is in flight so they cannot cover the result message.
    LaunchedEffect(busy) { InAppGate.set(context, "trade", busy) }
    DisposableEffect(Unit) { onDispose { InAppGate.set(context, "trade", false) } }

    // Prices refresh every 30 seconds; holdings load once (and again after every trade)
    LaunchedEffect(Unit) {
        try { owned = fetchOwned(token) } catch (e: Exception) { /* shown as 0 owned */ }
        while (true) {
            try {
                stocks = fetchStocks(token).first
                if (symbol == null) symbol = stocks?.firstOrNull()?.symbol
            } catch (e: AuthException) {
                if (e.code == 401) { onLogout(); return@LaunchedEffect }
                message = false to (e.message ?: "Something went wrong")
            } catch (e: Exception) {
                message = false to "Can't reach the server"
            }
            delay(30_000)
        }
    }

    fun placeOrder(stock: StockQuote) {
        // The app sends intent only; the backend fetches the authoritative live price and commits the trade.
        busy = true
        message = null
        scope.launch {
            try {
                val body = JSONObject().put("symbol", stock.symbol).put("type", if (isBuy) "BUY" else "SELL").put("quantity", qty)
                val r = request("POST", "/trades", body, token)
                onBalanceChange(r.getDouble("balance"))
                Analytics.tradePlaced(
                    context, stock.symbol, isBuy, qty,
                    r.getDouble("price"), r.getDouble("total"), user.balance, r.getDouble("balance"),
                    r.optBoolean("firstTrade")
                )
                owned = fetchOwned(token)
                message = true to "${if (isBuy) "Bought" else "Sold"} $qty ${stock.symbol} at ${money(r.getDouble("price"))} · ${money(r.getDouble("total"))}"
                qty = 1
            } catch (e: AuthException) {
                Analytics.tradeFailed(context, stock.symbol, isBuy, qty, "HTTP ${e.code}: ${e.message}")
                if (e.code == 401) onLogout() else message = false to (e.message ?: "Something went wrong")
            } catch (e: Exception) {
                Analytics.tradeFailed(context, stock.symbol, isBuy, qty, "network")
                message = false to "Can't reach the server"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Available balance", fontSize = 13.sp, color = SubtleText)
        Text(money(user.balance), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextGreen)

        Spacer(Modifier.height(20.dp))
        Text("Trade", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        Spacer(Modifier.height(10.dp))

        val list = stocks
        val stock = list?.firstOrNull { it.symbol == symbol }
        if (list == null || stock == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                if (message?.first == false) Text(message!!.second, color = Loss, fontSize = 14.sp)
                else CircularProgressIndicator(color = DarkGreen)
            }
            return@Column
        }

        // Stock picker
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach {
                val selected = it.symbol == symbol
                Text(
                    it.symbol,
                    color = if (selected) Color.White else TextGreen,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) DarkGreen else Color.White.copy(alpha = 0.55f))
                        .clickable { symbol = it.symbol; message = null; qty = 1 }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Selected stock: live price + change + shares owned
        Spacer(Modifier.height(14.dp))
        val own = owned[stock.symbol] ?: 0
        val changeColor = if (stock.change >= 0) Gain else Loss
        val sign = if (stock.change >= 0) "+" else "-"
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.55f)).padding(16.dp)
        ) {
            Text(stock.name, fontSize = 14.sp, color = SubtleText)
            Text(money(stock.price), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextGreen)
            Text("$sign${"%.2f".format(abs(stock.change))} ($sign${"%.2f".format(abs(stock.changePercent))}%)", fontSize = 14.sp, color = changeColor)
            Spacer(Modifier.height(8.dp))
            Text("You own $own ${if (own == 1) "share" else "shares"}", fontSize = 13.sp, color = SubtleText)
        }

        Spacer(Modifier.height(14.dp))
        ChartCard(stock.symbol, token)

        // Buy / Sell toggle
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "Buy", false to "Sell").forEach { (buy, label) ->
                val selected = isBuy == buy
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (selected) (if (buy) DarkGreen else Loss) else Color.White.copy(alpha = 0.55f))
                        .clickable { isBuy = buy; message = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (selected) Color.White else TextGreen)
                }
            }
        }

        // Quantity stepper
        Spacer(Modifier.height(20.dp))
        Text("Quantity", fontSize = 13.sp, color = SubtleText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { if (qty > 1) qty-- }
            Text(
                qty.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextGreen,
                modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            StepButton("+") { if (qty < 10000) qty++ }
        }

        // Estimated total + client-side hint (the server checks again)
        Spacer(Modifier.height(16.dp))
        val total = stock.price * qty
        Row(Modifier.fillMaxWidth()) {
            Text(if (isBuy) "Estimated cost" else "Estimated proceeds", fontSize = 15.sp, color = SubtleText, modifier = Modifier.weight(1f))
            Text(money(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        }
        val problem = when {
            isBuy && total > user.balance -> "Insufficient balance"
            !isBuy && qty > own -> if (own == 0) "You don't own any ${stock.symbol}" else "You only own $own"
            else -> null
        }
        problem?.let { Text(it, color = Loss, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { placeOrder(stock) },
            enabled = !busy && problem == null,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBuy) DarkGreen else Loss, contentColor = Color.White,
                disabledContainerColor = SubtleText.copy(alpha = 0.4f), disabledContentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (busy) "Placing order..." else "${if (isBuy) "Buy" else "Sell"} $qty ${stock.symbol}", fontSize = 17.sp)
        }

        message?.let { (ok, text) ->
            Text(text, color = if (ok) Gain else Loss, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Text("Orders execute at the live market price when you confirm.", fontSize = 12.sp, color = SubtleText, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(DarkGreen).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
