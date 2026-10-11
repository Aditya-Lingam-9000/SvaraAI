package com.sarvaai.app.service.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver to restart the wake word service on system boot, package update, or user unlock.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver onReceive action=$action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> {
                Log.i(TAG, "Starting SvaraWakeWordService from BootReceiver for action: $action")
                SvaraWakeWordService.startService(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
