package com.sarvaai.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.sarvaai.app.service.wakeword.SvaraWakeWordService

class SarvaApplication : Application() {
    companion object {
        const val TAG = "SarvaAI"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "==================================================")
        Log.i(TAG, "SarvaAI Initialized on Host Android Device")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        Log.i(TAG, "OS Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Log.i(TAG, "Hardware: ${Build.HARDWARE}, ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "Application ID: ${BuildConfig.APPLICATION_ID}")
        Log.i(TAG, "==================================================")

        try {
            SvaraWakeWordService.startService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SvaraWakeWordService in SarvaApplication: ${e.message}", e)
        }
    }
}
