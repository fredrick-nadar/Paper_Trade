package com.fredrick.papertrade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.pushnotification.CTPushNotificationListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging


class MainActivity : ComponentActivity(), CTPushNotificationListener {

    var cleverTapDefaultInstance: CleverTapAPI? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleverTapDefaultInstance = CleverTapAPI.getDefaultInstance(applicationContext)?.apply {
            ctPushNotificationListener = this@MainActivity
        }
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        registerFcmToken()
        requestNotificationPermission()

        setContent {
            PaperTradeApp(onUserAuthenticated = ::pushUserToAnalyticsAndCleverTap)
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cleverTapDefaultInstance?.pushFcmRegistrationId(task.result, true)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun pushUserToAnalyticsAndCleverTap(user: User) {
        val profileUpdate = hashMapOf<String, Any>(
            "Identity" to user.id.toString(),
            "Name" to user.name,
            "Email" to user.email,
            "Account Balance" to user.balance
        )
        cleverTapDefaultInstance?.onUserLogin(profileUpdate)

        firebaseAnalytics.setUserId(user.id.toString())

        val loginParams = Bundle().apply {
            putString("user_id", user.id.toString())
            putString("method", "paper_trade_backend")
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, loginParams)
    }

    override fun onNotificationClickedPayloadReceived(payload: HashMap<String, Any>) {

    }
}

@Composable
fun PaperTradeApp(onUserAuthenticated: (User) -> Unit = {}) {
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

    // If a JWT was saved earlier, check it with the backend before showing the login screen
    var checkingSession by remember { mutableStateOf(prefs.getString("token", null) != null) }
    LaunchedEffect(Unit) {
        val saved = prefs.getString("token", null) ?: return@LaunchedEffect
        try {
            user = fetchCurrentUser(saved)
            user?.let(onUserAuthenticated)
        } catch (e: AuthException) {
            if (e.code == 401) prefs.edit().remove("token").apply()   // expired (48h) or invalid
        } catch (e: Exception) {
            // Server unreachable: keep the token, just show the login screen
        }
        checkingSession = false
    }

    Crossfade(targetState = showLogin, label = "app") { isLogin ->
        if (isLogin) {
            val u = user
            if (checkingSession) Box(Modifier.fillMaxSize().background(Color(0xFFCFE8D2)))
            else if (u == null) AuthScreen(onAuthenticated = { token, loggedIn ->
                prefs.edit().putString("token", token).apply()
                user = loggedIn
                onUserAuthenticated(loggedIn)
            })
            else MainScreen(u, token = prefs.getString("token", "") ?: "", onLogout = {
                prefs.edit().remove("token").apply()
                user = null
            })
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
