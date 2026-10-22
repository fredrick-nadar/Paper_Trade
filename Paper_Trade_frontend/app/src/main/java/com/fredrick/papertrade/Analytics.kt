package com.fredrick.papertrade

import android.content.Context
import com.clevertap.android.sdk.CleverTapAPI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.Locale

/** CleverTap event and profile helpers. Every call is a no-op if the SDK is unavailable. */
object Analytics {
    /** Balance under which a user gets the "Low Balance Reached" nudge (10% of the $1,000 starting balance). */
    const val LOW_BALANCE_THRESHOLD = 100.0

    private fun ct(context: Context) = CleverTapAPI.getDefaultInstance(context.applicationContext)

    private fun track(context: Context, name: String, props: Map<String, Any> = emptyMap()) {
        ct(context)?.pushEvent(name, props)
    }

    // Shown and clicked events are not sent here: the SDK records Notification Viewed/Clicked itself.
    fun inAppDismissed(context: Context, campaignId: String, template: String = "", reason: String = "") =
        track(context, "InApp Dismissed", mapOf("Campaign Id" to campaignId, "Template" to template, "Reason" to reason))

    fun pushPermissionResponse(context: Context, accepted: Boolean) =
        track(context, "Push Permission Response", mapOf("Accepted" to accepted))

    fun screenViewed(context: Context, screen: String) =
        track(context, "Screen Viewed", mapOf("Screen" to screen))

    fun onboardingViewed(context: Context, slide: Int) =
        track(context, "Onboarding Viewed", mapOf("Slide Number" to slide))

    fun onboardingCompleted(context: Context) = track(context, "Onboarding Completed")

    fun loggedOut(context: Context) = track(context, "Logged Out")

    fun authFailed(context: Context, isSignup: Boolean, reason: String) =
        track(context, if (isSignup) "Signup Failed" else "Login Failed", mapOf("Reason" to reason))

    fun chartRangeChanged(context: Context, symbol: String, range: String) =
        track(context, "Chart Range Changed", mapOf("Symbol" to symbol, "Range" to range))

    fun chartTypeChanged(context: Context, symbol: String, type: String) =
        track(context, "Chart Type Changed", mapOf("Symbol" to symbol, "Type" to type))

    fun signedUp(context: Context) = track(context, "Signed Up", mapOf("Method" to "email"))

    fun loggedIn(context: Context) = track(context, "Logged In", mapOf("Method" to "email"))

    fun stockViewed(context: Context, symbol: String, price: Double) =
        track(context, "Stock Viewed", mapOf("Symbol" to symbol, "Price" to price))

    fun portfolioViewed(context: Context, holdingsCount: Int, totalValue: Double, pnl: Double) {
        track(
            context, "Portfolio Viewed",
            mapOf("Holdings Count" to holdingsCount, "Total Value" to totalValue, "Profit Loss" to pnl)
        )
        ct(context)?.pushProfile(mapOf("Holdings Count" to holdingsCount))
    }

    fun tradePlaced(
        context: Context, symbol: String, isBuy: Boolean, quantity: Int,
        price: Double, total: Double, balanceBefore: Double, balanceAfter: Double, firstTrade: Boolean
    ) {
        // The "make your first trade" banner is moot once the user trades.
        NativeDisplay.hide()
        // Attribute the trade to an in-app the user tapped in the last 24 hours.
        InAppHandler.recentClick(context)?.let { (campaign, action) ->
            track(context, "InApp Action Completed", mapOf("Campaign Id" to campaign, "Action" to action, "Completed" to "trade"))
        }
        track(
            context, "Trade Placed",
            mapOf(
                "Symbol" to symbol, "Side" to if (isBuy) "BUY" else "SELL", "Quantity" to quantity,
                "Price" to price, "Total" to total, "Balance After" to balanceAfter,
                "First Trade" to firstTrade
            )
        )
        // Separate event so a campaign can trigger on it directly; the server guarantees it fires once per user.
        if (firstTrade) {
            track(
                context, "First Trade Completed",
                mapOf("Symbol" to symbol, "Side" to if (isBuy) "BUY" else "SELL", "Quantity" to quantity, "Total" to total)
            )
        }
        ct(context)?.apply {
            // Charged event for money movement. Payment mode marks it as virtual so reports can filter it out.
            pushChargedEvent(
                hashMapOf<String, Any>(
                    "Amount" to total,
                    "Payment mode" to "Virtual Balance",
                    "Charged ID" to UUID.randomUUID().toString(),
                    "Side" to if (isBuy) "BUY" else "SELL"
                ),
                arrayListOf(
                    hashMapOf<String, Any>(
                        "Product Name" to symbol,
                        "Category" to if (isBuy) "Stock Buy" else "Stock Sell",
                        "Quantity" to quantity,
                        "Price" to price
                    )
                )
            )
            incrementValue("Total Trades", 1)
            // Lets segments target everyone who is currently low, not only those who just crossed the line.
            pushProfile(mapOf("Low Balance" to (balanceAfter < LOW_BALANCE_THRESHOLD)))
            pushProfile(
                mapOf(
                    "Account Balance" to balanceAfter,
                    "Last Trade Symbol" to symbol,
                    "Last Trade Date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                )
            )
            if (firstTrade) {
                pushProfile(mapOf("First Trade Date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())))
            }
        }
        lowBalanceCheck(context, balanceBefore, balanceAfter)
    }

    /** Fires once per crossing (not on every trade while low), so a campaign cannot spam the user. */
    private fun lowBalanceCheck(context: Context, before: Double, after: Double) {
        if (before >= LOW_BALANCE_THRESHOLD && after < LOW_BALANCE_THRESHOLD) {
            track(context, "Low Balance Reached", mapOf("Balance" to after, "Threshold" to LOW_BALANCE_THRESHOLD))
        }
    }

    fun tradeFailed(context: Context, symbol: String, isBuy: Boolean, quantity: Int, reason: String) =
        track(
            context, "Trade Failed",
            mapOf("Symbol" to symbol, "Side" to if (isBuy) "BUY" else "SELL", "Quantity" to quantity, "Reason" to reason)
        )
}
