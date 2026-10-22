package com.fredrick.papertrade

import android.content.Context
import com.clevertap.android.sdk.CleverTapAPI

/**
 * Holds CleverTap in-apps back while the user is somewhere they should not be interrupted (signed out,
 * mid-trade). The SDK queues suspended in-apps and shows them when the last reason is lifted.
 */
object InAppGate {
    private val reasons = mutableSetOf<String>()

    fun set(context: Context, reason: String, blocked: Boolean) {
        val wasBlocked = reasons.isNotEmpty()
        if (blocked) reasons.add(reason) else reasons.remove(reason)
        val isBlocked = reasons.isNotEmpty()
        if (wasBlocked == isBlocked) return
        val ct = CleverTapAPI.getDefaultInstance(context.applicationContext) ?: return
        if (isBlocked) ct.suspendInAppNotifications() else ct.resumeInAppNotifications()
    }
}
