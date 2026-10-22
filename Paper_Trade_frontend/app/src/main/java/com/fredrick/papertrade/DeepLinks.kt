package com.fredrick.papertrade

import android.net.Uri

/**
 * papertrade://stocks, papertrade://trade[?symbol=AAPL], papertrade://portfolio.
 * Used by in-app and push URL buttons. Links only switch tabs; they never place trades.
 */
object DeepLinks {
    private const val SCHEME = "papertrade"
    private val symbolPattern = Regex("[A-Z]{1,5}")

    /** Returns true when the link was ours and has been queued for MainScreen. */
    fun handle(uri: Uri?): Boolean {
        if (uri?.scheme != SCHEME) return false
        val action = when (uri.host) {
            "stocks" -> "open_stocks"
            "trade" -> "open_trade"
            "portfolio" -> "open_portfolio"
            else -> return false
        }
        // The symbol comes from outside the app, so only accept the same shape the backend accepts.
        InAppNav.symbol.value = uri.getQueryParameter("symbol")?.uppercase()?.takeIf(symbolPattern::matches)
        InAppNav.request.value = action
        return true
    }
}
