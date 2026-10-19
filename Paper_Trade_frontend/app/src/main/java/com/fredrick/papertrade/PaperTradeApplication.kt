package com.fredrick.papertrade

import com.clevertap.android.sdk.Application

/** Extends CleverTap's Application so its lifecycle hooks stay active, and registers custom in-app templates. */
class PaperTradeApplication : Application() {
    override fun onCreate() {
        // Templates must be registered before the CleverTapAPI instance is created (CleverTap docs).
        InAppTemplates.register(this)
        super.onCreate()
    }
}
