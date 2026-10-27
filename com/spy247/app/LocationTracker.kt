package com.spy247.app

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

/**
 * Location Tracker - Handles GPS location syncing to Firebase
 */
class LocationTracker(context: Context) : LocationListener {
    
    private val context = context.applicationContext
    private var isTracking = false
    
    companion object {
        const val LOCATION_UPDATE_INTERVAL_MS = 2000L // Update every 2 seconds
        const val MIN_DISTANCE_METERS = 10f // Only update if moved > 10 meters
    }
    
    init {
        setupLocationUpdates()
    }
    
    /**
     * Start location tracking and sync to Firebase
     */
    fun startTracking() {
        if (isTracking) return
        
        isTracking = true
        
        // Update location every 2 seconds
        val locationRef = context.getSystemService(LocationManager::class.java)
            ?.let { locationManager ->
                // Get last known location or use FusedLocationProvider
                locationManager.lastKnownLocation("gps")
            }
        
        // Send to Firebase live_location node
        LocationTracker(this, LOCATION_UPDATE_INTERVAL_MS).startUpdatingLocation()
    }
    
    /**
     * Stop location tracking
     */
    fun stopTracking() {
        if (!isTracking) return
        
        isTracking = false
        context.getSystemService(LocationManager::class.java)?.removeUpdates(this)
    }
    
    override fun onLocationChanged(location: Location) {
        // Called when GPS provides new location
        val latitude = location.latitude.toString()
        val longitude = location.longitude.toString()
        
        // Send to Firebase live_location node
        // database.getReference("live_location").setValue(mapOf(
        //     "latitude" to latitude,
        //     "longitude" to longitude,
        //     "accuracy" to location.accuracy,
        //     "timestamp" to location.time
        // ))
    }
    
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        // Handle provider status changes (GPS, Network, etc.)
    }
    
    override fun onProviderEnabled(provider: String) {
        // Called when user enables a location provider
    }
    
    override fun onProvidersDisabled() {
        // Called when all providers are disabled
    }
    
    /**
     * Setup initial location updates using FusedLocationProvider or individual providers
     */
    private fun setupLocationUpdates() {
        val locationManager = context.getSystemService(LocationManager::class.java) as LocationManager
        
        // Get the best fused location provider (usually "fused" for AndroidX)
        val provider = locationManager.supportedProviders.firstOrNull { it == "fused" } 
            ?: "gps" // Fallback to GPS if fused not available
        
        // Register this listener for the chosen provider
        locationManager.requestLocationUpdates(
            provider,
            LOCATION_UPDATE_INTERVAL_MS,
            MIN_DISTANCE_METERS,
            this
        )
    }
}