package com.spy247.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service placeholder so the manifest entry compiles.
 */
class ForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
