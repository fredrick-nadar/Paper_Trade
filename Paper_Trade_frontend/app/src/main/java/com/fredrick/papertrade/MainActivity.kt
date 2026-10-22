package com.fredrick.papertrade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
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


// CleverTap in-apps need a FragmentActivity (which is also a ComponentActivity, so Compose is unaffected).
class MainActivity : FragmentActivity(), CTPushNotificationListener {

    private val notificationTag = "PaperTradePush"
    var cleverTapDefaultInstance: CleverTapAPI? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val inAppHandler by lazy { InAppHandler(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // App startup initializes CleverTap/Firebase before Compose starts the login flow.
        super.onCreate(savedInstanceState)
        handlePushIntent(intent)
        DeepLinks.handle(intent?.data)
        // No in-apps until the user is signed in; PaperTradeApp lifts this.
        InAppGate.set(this, "signed_out", true)
        cleverTapDefaultInstance = CleverTapAPI.getDefaultInstance(applicationContext)?.apply {
            ctPushNotificationListener = this@MainActivity
        }
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        cleverTapDefaultInstance?.let(inAppHandler::register)
        // Publishes our custom templates to the dashboard. CleverTap says: debug builds with a test user only.
        if (BuildConfig.DEBUG) cleverTapDefaultInstance?.syncRegisteredInAppTemplates()
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
        DeepLinks.handle(intent.data)
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
        // The first launches are left to the CleverTap push-primer in-app, which asks with context;
        // if it never granted permission, fall back to the plain system prompt from the third launch on.
        val prefs = getSharedPreferences("paper_trade_prefs", Context.MODE_PRIVATE)
        val launches = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launches).apply()
        if (launches >= 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleverTapDefaultInstance?.let(inAppHandler::unregister)
    }

    private fun pushUserToAnalyticsAndCleverTap(user: User) {
        // Login links the backend user to the CleverTap profile and identifies the same user in Analytics.
        val profileUpdate = hashMapOf<String, Any>(
            "Identity" to user.publicId,
            "Name" to user.name,
            "Email" to user.email,
            "Account Balance" to user.balance,
            "Low Balance" to (user.balance < Analytics.LOW_BALANCE_THRESHOLD)
        )
        cleverTapDefaultInstance?.onUserLogin(profileUpdate)
        registerFcmToken()

        firebaseAnalytics.setUserId(user.publicId)

        val loginParams = Bundle().apply {
            putString("user_id", user.publicId)
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

    // Campaign in-apps wait until the user is signed in.
    LaunchedEffect(user) { InAppGate.set(context, "signed_out", user == null) }

    // If a JWT was saved earlier, check it with the backend before showing the login screen
    var checkingSession by remember { mutableStateOf(prefs.getString("token", null) != null) }
    // A saved JWT is checked against /auth/me before the login screen is shown.
    LaunchedEffect(Unit) {
        val saved = prefs.getString("token", null) ?: return@LaunchedEffect
        try {
            user = fetchCurrentUser(saved)
            user?.let(onUserAuthenticated)
        } catch (e: AuthException) {
            if (e.code == 401) {
                prefs.edit().remove("token").apply()
            }
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
                    Analytics.onboardingCompleted(context)
                    showLogin = true
                }
            )
        }
    }

    // CleverTap custom-template in-apps (welcome, nudges, push primer) render above any screen.
    InAppOverlayHost()
}
