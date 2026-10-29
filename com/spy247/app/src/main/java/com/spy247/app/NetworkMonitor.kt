package com.spy247.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log

/**
 * NetworkMonitor - Handles WiFi disconnection detection and auto-reconnect logic
 * Monitors network connectivity and updates Firebase status when connection changes.
 */
class NetworkMonitor(context: Context) {

    private val context = context.applicationContext
    private var isMonitoring = false

    // Callback instance kept as a field so it can be unregistered later
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "NetworkMonitor"
        const val HEARTBEAT_INTERVAL_MS = 5000L // 5 seconds between heartbeats
    }

    /**
     * Start monitoring network connectivity changes
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active")
            return
        }

        isMonitoring = true

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                broadcastStatus("online")
                reestablishStreams()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost")
                broadcastStatus("offline")
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d(TAG, "Network losing: $maxMsToLive ms remaining")
            }

            override fun onUnavailable() {
                Log.w(TAG, "Network unavailable")
                broadcastStatus("offline")
            }
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Build a request for all network types (WiFi + mobile)
        val builder = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)

        try {
            cm.registerNetworkCallback(builder.build(), networkCallback!!)
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
            isMonitoring = false
        }
    }

    /**
     * Stop monitoring network connectivity
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = networkCallback
        if (callback != null) {
            try {
                cm.unregisterNetworkCallback(callback)
                Log.d(TAG, "Network monitoring stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }

        networkCallback = null
    }

    /**
     * Broadcast current status to local storage
     */
    private fun broadcastStatus(status: String) {
        val editor = context.getSharedPreferences("spy247", Context.MODE_PRIVATE).edit()
        editor.putString("connectivity_status", status).apply()
        Log.d(TAG, "Status broadcast: $status")
    }

    /**
     * Re-establish all media streams when connection comes back
     */
    private fun reestablishStreams() {
        // Placeholder: restart streams if they were active
        // Future: hook into MediaManager here
        Log.d(TAG, "Re-establishing streams")
    }

    /**
     * Get current connectivity status
     */
    fun getStatus(): String {
        return context.getSharedPreferences("spy247", Context.MODE_PRIVATE)
            .getString("connectivity_status", "unknown") ?: "unknown"
    }
}