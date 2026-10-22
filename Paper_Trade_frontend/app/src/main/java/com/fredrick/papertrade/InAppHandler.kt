package com.fredrick.papertrade

import android.content.Context
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.inapp.CTInAppNotification
import com.clevertap.android.sdk.InAppNotificationButtonListener
import com.clevertap.android.sdk.InAppNotificationListener
import com.clevertap.android.sdk.PushPermissionResponseListener
import kotlinx.coroutines.flow.MutableStateFlow

/** Tab requests coming from in-app buttons. MainScreen consumes the value and resets it. */
object InAppNav {
    val request = MutableStateFlow<String?>(null)

    // Optional stock to preselect when the request is open_trade (from a papertrade://trade?symbol=AAPL link).
    val symbol = MutableStateFlow<String?>(null)

    // Button key-value pair "action" -> tab index in MainScreen.
    fun tabFor(action: String): Int? = when (action) {
        "open_stocks" -> 0
        "open_trade" -> 1
        "open_portfolio" -> 2
        else -> null
    }
}

/**
 * Receives CleverTap in-app callbacks. Dashboard campaigns pass an "action" key-value pair on buttons
 * (open_stocks, open_trade, open_portfolio); the push-primer template is handled by the SDK itself.
 */
class InAppHandler(context: Context) :
    InAppNotificationListener, InAppNotificationButtonListener, PushPermissionResponseListener {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("inapp_state", Context.MODE_PRIVATE)

    fun register(ct: CleverTapAPI) {
        ct.setInAppNotificationListener(this)
        ct.setInAppNotificationButtonListener(this)
        ct.registerPushPermissionNotificationResponseListener(this)
    }

    fun unregister(ct: CleverTapAPI) {
        ct.unregisterPushPermissionNotificationResponseListener(this)
    }

    // Always allow display; returning false here would suppress the message.
    override fun beforeShow(extras: MutableMap<String, Any>?): Boolean = true

    override fun onShow(ctInAppNotification: CTInAppNotification?) {
        val id = ctInAppNotification?.campaignId.orEmpty()
        prefs.edit().putString("last_campaign", id).apply()
    }

    override fun onDismissed(extras: MutableMap<String, Any>?, actionExtras: MutableMap<String, Any>?) {
        Analytics.inAppDismissed(appContext, prefs.getString("last_campaign", "").orEmpty())
    }

    override fun onInAppButtonClick(payload: HashMap<String, String>?) {
        val action = payload?.get("action") ?: return
        val campaign = prefs.getString("last_campaign", "").orEmpty()
        prefs.edit().putLong("last_click_ms", System.currentTimeMillis()).putString("last_action", action).apply()
        InAppNav.request.value = action
    }

    override fun onPushPermissionResponse(accepted: Boolean) {
        Analytics.pushPermissionResponse(appContext, accepted)
    }

    companion object {
        /** Remembers the last tapped in-app so a later trade can be attributed to it. */
        fun rememberClick(context: Context, campaign: String, action: String) {
            context.applicationContext.getSharedPreferences("inapp_state", Context.MODE_PRIVATE).edit()
                .putString("last_campaign", campaign).putString("last_action", action)
                .putLong("last_click_ms", System.currentTimeMillis()).apply()
        }

        /** (campaign id, action) when the user tapped an in-app button in the last 24 hours, else null. */
        fun recentClick(context: Context): Pair<String, String>? {
            val prefs = context.applicationContext.getSharedPreferences("inapp_state", Context.MODE_PRIVATE)
            val at = prefs.getLong("last_click_ms", 0L)
            if (System.currentTimeMillis() - at > 24 * 60 * 60 * 1000L) return null
            return prefs.getString("last_campaign", "").orEmpty() to prefs.getString("last_action", "").orEmpty()
        }
    }
}
