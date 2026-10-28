package com.fredrick.papertrade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clevertap.android.sdk.CleverTapAPI
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFcmMessageListenerService : FirebaseMessagingService() {

    // The service receives CleverTap's data-only FCM payload and creates the Android notification.
    private val channelId = "paper_trade_push"

    override fun onNewToken(token: String) {
        // FCM can rotate tokens; every new value must be sent back to CleverTap.
        super.onNewToken(token)
        Log.d("PaperTradePush", "FCM token refreshed")
        CleverTapAPI.getDefaultInstance(applicationContext)
            ?.pushFcmRegistrationId(token, true)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Campaign delivery path: FCM -> this service -> CleverTap payload parser -> Android notification.
        Log.d("PaperTradePush", "FCM message received; data=${message.data}")
        try {
            val extras = Bundle()
            message.data.forEach { (key, value) -> extras.putString(key, value) }

            val notificationInfo = CleverTapAPI.getNotificationInfo(extras)
            Log.d("PaperTradePush", "CleverTap payload recognized=${notificationInfo.fromCleverTap}")
            if (notificationInfo.fromCleverTap) {
                // Record the impression explicitly because the fallback notification is rendered locally.
                CleverTapAPI.getDefaultInstance(applicationContext)
                    ?.pushNotificationViewedEvent(extras)
                CleverTapAPI.createNotification(applicationContext, extras)
                // The fallback guarantees visible delivery on devices where the SDK renderer does not post.
                Handler(Looper.getMainLooper()).postDelayed({
                    postFallbackNotification(
                        extras,
                        extras.getString("nt").orEmpty(),
                        extras.getString("nm").orEmpty()
                    )
                }, 500)
            }
        } catch (error: Throwable) {
            Log.e("PaperTradePush", "Unable to create CleverTap notification", error)
        }
    }

    private fun postFallbackNotification(extras: Bundle, title: String, body: String) {
        // Uses a unique ID and carries the original CleverTap extras so multiple pushes and clicks work.
        val manager = getSystemService(NotificationManager::class.java)
        // A campaign that sets Channel ID = trade_alerts gets the custom sound; anything else uses the default channel.
        val useSound = extras.getString("wzrk_cid") == PaperTradeApplication.SOUND_CHANNEL_ID
        if (useSound) {
            PaperTradeApplication.createSoundChannel(applicationContext)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Paper Trade notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(extras)
        }
        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            notificationId,
            NotificationCompat.Builder(this, if (useSound) PaperTradeApplication.SOUND_CHANNEL_ID else channelId)
                .setSmallIcon(com.fredrick.papertrade.R.drawable.ic_launcher_foreground)
                .setContentTitle(title.ifBlank { getString(R.string.app_name) })
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        )
        Log.d("PaperTradePush", "Fallback notification posted")
    }
}
