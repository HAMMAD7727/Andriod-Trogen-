package com.spy247.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**\n * Foreground service placeholder so the manifest entry compiles.\n */\nclass ForegroundService : Service() {\n    override fun onBind(intent: Intent?): IBinder? = null\n}
