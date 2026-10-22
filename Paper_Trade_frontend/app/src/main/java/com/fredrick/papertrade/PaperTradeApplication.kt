package com.fredrick.papertrade

import com.clevertap.android.sdk.Application
import com.clevertap.android.sdk.CleverTapAPI

/** Extends CleverTap's Application so its lifecycle hooks stay active, and registers custom in-app templates. */
class PaperTradeApplication : Application() {
    override fun onCreate() {
        // Templates must be registered before the CleverTapAPI instance is created (CleverTap docs).
        InAppTemplates.register(this)
        // Logcat tag "CleverTap": shows events sent and why an in-app was shown or skipped.
        if (BuildConfig.DEBUG) CleverTapAPI.setDebugLevel(CleverTapAPI.LogLevel.VERBOSE)
        super.onCreate()
    }
}
