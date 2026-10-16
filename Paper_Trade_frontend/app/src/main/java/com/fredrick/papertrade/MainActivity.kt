package com.fredrick.papertrade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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

    private val notificationTag = "PaperTradePush"
    var cleverTapDefaultInstance: CleverTapAPI? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        // App startup initializes CleverTap/Firebase before Compose starts the login flow.
        super.onCreate(savedInstanceState)
        handlePushIntent(intent)
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    private fun handlePushIntent(intent: android.content.Intent?) {
        // Fallback notifications carry the CleverTap bundle here so clicks are attributed to the campaign.
        val pushExtras = intent?.extras ?: return
        if (pushExtras.containsKey("wzrk_pn") ||
            pushExtras.containsKey("wzrk_id")
        ) {
            CleverTapAPI.getDefaultInstance(applicationContext)
                ?.pushNotificationClickedEvent(pushExtras)
            Log.d("PaperTradePush", "CleverTap click event recorded")
        }
    }

    private fun registerFcmToken() {
        // CleverTap targets this device using the FCM token, not the user's email address alone.
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(notificationTag, "FCM token received")
                cleverTapDefaultInstance?.pushFcmRegistrationId(token, true)
            } else {
                Log.e(notificationTag, "Unable to retrieve FCM token", task.exception)
            }
        }
    }

    private fun requestNotificationPermission() {
        // Android 13+ requires explicit permission before any push can be displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun pushUserToAnalyticsAndCleverTap(user: User) {
        // Login links the backend user to the CleverTap profile and identifies the same user in Analytics.
        val profileUpdate = hashMapOf<String, Any>(
            "Identity" to user.id.toString(),
            "Name" to user.name,
            "Email" to user.email,
            "Account Balance" to user.balance
        )
        cleverTapDefaultInstance?.onUserLogin(profileUpdate)
        registerFcmToken()

        firebaseAnalytics.setUserId(user.id.toString())

        val loginParams = Bundle().apply {
            putString("user_id", user.id.toString())
            putString("method", "paper_trade_backend")
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, loginParams)
    }

    override fun onNotificationClickedPayloadReceived(payload: HashMap<String, Any>) {
        // Clicks from the custom fallback notification are handled through the Activity intent above.
    }
}

/** Owns onboarding/session state and chooses between onboarding, auth, and the logged-in shell. */
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
    // A saved JWT is checked against /auth/me before the login screen is shown.
    LaunchedEffect(Unit) {
        val saved = prefs.getString("token", null) ?: return@LaunchedEffect
        BackendKeepAliveScheduler.start(context)
        try {
            user = fetchCurrentUser(saved)
            user?.let(onUserAuthenticated)
        } catch (e: AuthException) {
            if (e.code == 401) {
                prefs.edit().remove("token").apply()
                BackendKeepAliveScheduler.stop(context)
            }
        } catch (e: Exception) {
            // Server unreachable: keep the token, just show the login screen
        }
        checkingSession = false
    }

    LaunchedEffect(user) {
        if (user == null) {
            BackendKeepAliveScheduler.stop(context)
            return@LaunchedEffect
        }

        BackendKeepAliveScheduler.start(context)
    }

    Crossfade(targetState = showLogin, label = "app") { isLogin ->
        if (isLogin) {
            val u = user
            if (checkingSession) Box(Modifier.fillMaxSize().background(Color(0xFFCFE8D2)))
            else if (u == null) AuthScreen(onAuthenticated = { token, loggedIn ->
                prefs.edit().putString("token", token).apply()
                user = loggedIn
                BackendKeepAliveScheduler.start(context)
                onUserAuthenticated(loggedIn)
            })
            else MainScreen(u, token = prefs.getString("token", "") ?: "", onLogout = {
                prefs.edit().remove("token").apply()
                user = null
                BackendKeepAliveScheduler.stop(context)
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
