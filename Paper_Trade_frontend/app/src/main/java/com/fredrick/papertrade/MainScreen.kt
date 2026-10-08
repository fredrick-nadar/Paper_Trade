package com.fredrick.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor

private val Background = Color(0xFFCFE8D2)
private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)

// Simple 24x24 icons, drawn from SVG path data (avoids pulling in the big icons library)
private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

private val StocksIcon = icon("stocks", "M3.5,18.49l6,-6.01l4,4L22,6.92l-1.41,-1.41l-7.09,7.97l-4,-4L2,16.99z")
private val TradeIcon = icon("trade", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
private val DashboardIcon = icon("dashboard", "M3,13h8L11,3L3,3v10zM3,21h8v-6L3,15v6zM13,21h8L21,11h-8v10zM13,3v6h8L21,3h-8z")

private val tabs = listOf("Stocks" to StocksIcon, "Trade" to TradeIcon, "Dashboard" to DashboardIcon)

/** Logged-in app shell: the current tab's screen on top, bottom navigation bar below. */
@Composable
fun MainScreen(user: User, token: String, onLogout: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(user) }              // keeps the balance up to date after trades
    var tradeSymbol by remember { mutableStateOf<String?>(null) } // stock tapped on the Stocks tab

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> StocksScreen(current, token, onLogout = onLogout, onStockClick = {
                    tradeSymbol = it
                    selected = 1
                })
                1 -> TradeScreen(current, token, tradeSymbol, onBalanceChange = { current = current.copy(balance = it) }, onLogout = onLogout)
                else -> ComingSoon("Dashboard", "Holdings and profit/loss will show here")
            }
        }

        NavigationBar(containerColor = DarkGreen) {
            tabs.forEachIndexed { index, (label, icon) ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreen,
                        selectedTextColor = Color.White,
                        indicatorColor = Background,
                        unselectedIconColor = Background.copy(alpha = 0.7f),
                        unselectedTextColor = Background.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextGreen)
        Text(subtitle, fontSize = 15.sp, color = SubtleText)
    }
}
