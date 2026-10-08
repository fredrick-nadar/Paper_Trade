package com.fredrick.papertrade

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaperTradeApp() }
    }
}

@Composable
fun PaperTradeApp() {
    val context = LocalContext.current

    // Opens (or creates) a small storage file named "paper_trade_prefs"
    val prefs = remember {
        context.getSharedPreferences("paper_trade_prefs", Context.MODE_PRIVATE)
    }

    // Start on the Login screen if onboarding was already completed.
    // getBoolean returns false the very first time (nothing saved yet).
    var showLogin by remember {
        mutableStateOf(prefs.getBoolean("onboarding_done", false))
    }

    var user by remember { mutableStateOf<User?>(null) }

    Crossfade(targetState = showLogin, label = "app") { isLogin ->
        if (isLogin) {
            val u = user
            if (u == null) AuthScreen(onAuthenticated = { user = it })
            else HomePlaceholder(u, onLogout = { user = null })
        } else {
            OnboardingScreen(
                onFinished = {
                    // Remember that onboarding is done, then move on
                    prefs.edit().putBoolean("onboarding_done", true).apply()
                    showLogin = true
                }
            )
        }
    }
}
