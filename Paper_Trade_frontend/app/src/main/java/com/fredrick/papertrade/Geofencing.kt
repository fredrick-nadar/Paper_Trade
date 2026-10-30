package com.fredrick.papertrade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.clevertap.android.geofence.CTGeofenceAPI
import com.clevertap.android.geofence.CTGeofenceSettings
import com.clevertap.android.geofence.Logger
import com.clevertap.android.geofence.interfaces.CTGeofenceEventsListener
import com.clevertap.android.sdk.CleverTapAPI
import org.json.JSONObject

/**
 * CleverTap geofencing. The dashboard owns the zones (clusters); the SDK raises "Geofence Cluster Entered/Exited".
 * Any cluster whose name starts with [RESTRICTED_PREFIX] blocks buying [BLOCKED_SYMBOLS] while the user is inside it.
 */
object Geofencing {
    /** Name the dashboard geofence cluster with this prefix, e.g. "Restricted - Kalyan". */
    const val RESTRICTED_PREFIX = "restricted"
    val BLOCKED_SYMBOLS = setOf("AAPL", "GOOGL")

    private const val PREFS = "paper_trade_prefs"
    private const val KEY_ZONES = "geofence_restricted_zones"
    private const val TAG = "PaperTradeGeofence"

    // Compose reads this, so the Buy button reacts the moment an enter/exit event arrives.
    private var zones by mutableStateOf<Set<String>>(emptySet())
    private var started = false

    val inRestrictedZone: Boolean get() = zones.isNotEmpty()

    fun isBlocked(symbol: String) = inRestrictedZone && symbol in BLOCKED_SYMBOLS

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Idempotent. Needs location permission, so call it at launch and again after the user grants it. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (started || !hasLocationPermission(app)) return
        val ct = CleverTapAPI.getDefaultInstance(app) ?: return
        zones = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_ZONES, emptySet()).orEmpty()

        val settings = CTGeofenceSettings.Builder()
            .enableBackgroundLocationUpdates(true)
            .setLogLevel(if (BuildConfig.DEBUG) Logger.DEBUG else Logger.OFF)
            .setLocationAccuracy(CTGeofenceSettings.ACCURACY_HIGH)
            .setLocationFetchMode(CTGeofenceSettings.FETCH_CURRENT_LOCATION_PERIODIC)
            .setGeofenceMonitoringCount(CTGeofenceSettings.DEFAULT_GEO_MONITOR_COUNT)
            .build()
        val api = CTGeofenceAPI.getInstance(app)
        // Listener first, so an event raised right after init is not missed.
        api.setCtGeofenceEventsListener(object : CTGeofenceEventsListener {
            override fun onGeofenceEnteredEvent(jsonObject: JSONObject) = update(app, jsonObject, entered = true)
            override fun onGeofenceExitedEvent(jsonObject: JSONObject) = update(app, jsonObject, entered = false)
        })
        api.init(settings, ct)
        started = true
    }

    private fun update(context: Context, event: JSONObject, entered: Boolean) {
        val name = event.optString("gcName")
        Log.d(TAG, "${if (entered) "Entered" else "Exited"} cluster '$name'")
        if (!name.startsWith(RESTRICTED_PREFIX, ignoreCase = true)) return
        zones = if (entered) zones + name else zones - name
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_ZONES, zones).apply()
        // Lets segments and campaigns target people currently inside a restricted zone.
        CleverTapAPI.getDefaultInstance(context)?.pushProfile(mapOf("In Restricted Zone" to inRestrictedZone))
    }
}
