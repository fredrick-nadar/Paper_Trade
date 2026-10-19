package com.fredrick.papertrade

import android.content.Context
import com.clevertap.android.sdk.CleverTapAPI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.Locale

/** CleverTap event and profile helpers. Every call is a no-op if the SDK is unavailable. */
object Analytics {
    private fun ct(context: Context) = CleverTapAPI.getDefaultInstance(context.applicationContext)

    private fun track(context: Context, name: String, props: Map<String, Any> = emptyMap()) {
        ct(context)?.pushEvent(name, props)
    }

    fun inAppShown(context: Context, campaignId: String) =
        track(context, "InApp Shown", mapOf("Campaign Id" to campaignId))

    fun inAppDismissed(context: Context, campaignId: String) =
        track(context, "InApp Dismissed", mapOf("Campaign Id" to campaignId))

    fun inAppButtonClicked(context: Context, campaignId: String, action: String) =
        track(context, "InApp Button Clicked", mapOf("Campaign Id" to campaignId, "Action" to action))

    fun pushPermissionResponse(context: Context, accepted: Boolean) =
        track(context, "Push Permission Response", mapOf("Accepted" to accepted))

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
        price: Double, total: Double, balanceAfter: Double
    ) {
        // Attribute the trade to an in-app the user tapped in the last 24 hours.
        InAppHandler.recentClick(context)?.let { (campaign, action) ->
            track(context, "InApp Action Completed", mapOf("Campaign Id" to campaign, "Action" to action, "Completed" to "trade"))
        }
        track(
            context, "Trade Placed",
            mapOf(
                "Symbol" to symbol, "Side" to if (isBuy) "BUY" else "SELL", "Quantity" to quantity,
                "Price" to price, "Total" to total, "Balance After" to balanceAfter
            )
        )
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
            pushProfile(
                mapOf(
                    "Account Balance" to balanceAfter,
                    "Last Trade Symbol" to symbol,
                    "Last Trade Date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                )
            )
        }
    }

    fun tradeFailed(context: Context, symbol: String, isBuy: Boolean, quantity: Int, reason: String) =
        track(
            context, "Trade Failed",
            mapOf("Symbol" to symbol, "Side" to if (isBuy) "BUY" else "SELL", "Quantity" to quantity, "Reason" to reason)
        )
}
