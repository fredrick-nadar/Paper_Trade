package com.fredrick.papertrade

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the backend for each request. Debug builds use a local dev server when one answers
 * (physical phone via `adb reverse tcp:8080 tcp:8080`), otherwise production. Release builds always use production.
 */
object Backend {
    private const val LOCAL_URL = "http://localhost:8080"
    private const val PROBE_TIMEOUT_MILLIS = 800

    @Volatile
    private var resolved: String? = null

    suspend fun baseUrl(): String {
        resolved?.let { return it }
        val url = if (BuildConfig.DEBUG) findLocal() ?: BuildConfig.BACKEND_URL else BuildConfig.BACKEND_URL
        resolved = url
        return url
    }

    /** Forget the choice so the next request probes again, e.g. after the local server was started or stopped. */
    fun reset() {
        resolved = null
    }

    private suspend fun findLocal(): String? = LOCAL_URL.takeIf { isUp(it) }

    private suspend fun isUp(base: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$base/").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = PROBE_TIMEOUT_MILLIS
                conn.readTimeout = PROBE_TIMEOUT_MILLIS
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
