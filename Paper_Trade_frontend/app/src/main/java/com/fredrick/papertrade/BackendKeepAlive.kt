package com.fredrick.papertrade

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val KEEP_ALIVE_ACTION = "com.fredrick.papertrade.BACKEND_KEEP_ALIVE"
private const val KEEP_ALIVE_INTERVAL_MILLIS = 14 * 60 * 1_000L
private const val PING_TIMEOUT_MILLIS = 5_000
private const val TOKEN_KEY = "token"

private fun keepAliveIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BackendKeepAliveReceiver::class.java).setAction(KEEP_ALIVE_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

/** Schedules a self-rescheduling alarm while a login token exists. */
object BackendKeepAliveScheduler {
    fun start(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + KEEP_ALIVE_INTERVAL_MILLIS
        val operation = keepAliveIntent(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation
            )
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.getSystemService(AlarmManager::class.java)
            .cancel(keepAliveIntent(appContext))
    }
}

/** Pings Render and schedules the next ping even when the app process is not running. */
class BackendKeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KEEP_ALIVE_ACTION) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val hasSession = appContext
            .getSharedPreferences("paper_trade_prefs", Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) != null

        if (!hasSession) {
            BackendKeepAliveScheduler.stop(appContext)
            pendingResult.finish()
            return
        }

        BackendKeepAliveScheduler.start(appContext)
        thread(name = "backend-keep-alive", start = true) {
            try {
                pingBackend()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Sends a lightweight request to the unauthenticated Render health endpoint. */
private fun pingBackend(): Boolean {
    val connection = URL(BuildConfig.BACKEND_URL).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = PING_TIMEOUT_MILLIS
        connection.readTimeout = PING_TIMEOUT_MILLIS
        connection.responseCode in 200..399
    } finally {
        connection.disconnect()
    }
}

/** Restores the session keep-alive after Android restarts the device or updates the app. */
class BackendKeepAliveBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val hasSession = context
            .getSharedPreferences("paper_trade_prefs", Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) != null
        if (hasSession) BackendKeepAliveScheduler.start(context)
    }
}
