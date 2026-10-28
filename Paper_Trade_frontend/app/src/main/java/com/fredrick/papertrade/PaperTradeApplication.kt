package com.fredrick.papertrade

import android.app.NotificationManager
import android.content.Context
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
        createSoundChannel(this)
    }

    companion object {
        /** Channel ID to enter in the CleverTap campaign (Advanced > Channel ID) to play [SOUND_FILE]. */
        const val SOUND_CHANNEL_ID = "trade_alerts"
        const val SOUND_FILE = "mixkit_correct_answer_tone_2870.wav"

        /** Android fixes a channel's sound at creation, so the custom-sound channel must exist before any push arrives. */
        fun createSoundChannel(context: Context) {
            CleverTapAPI.createNotificationChannel(
                context,
                SOUND_CHANNEL_ID,
                "Trade alerts",
                "Trade notifications with a custom sound",
                NotificationManager.IMPORTANCE_HIGH,
                true,
                SOUND_FILE
            )
        }
    }
}
