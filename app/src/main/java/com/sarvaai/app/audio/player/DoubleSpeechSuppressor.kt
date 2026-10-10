package com.sarvaai.app.audio.player

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class DoubleSpeechSuppressor(private val context: Context) {
    companion object {
        private const val TAG = "DoubleSpeechSuppressor"
    }

    private val accessibilityManager: AccessibilityManager? by lazy {
        try {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        } catch (t: Throwable) {
            Log.w(TAG, "Could not obtain AccessibilityManager", t)
            null
        }
    }

    /**
     * Checks whether TalkBack / Touch Exploration or another screen reader is currently active.
     */
    val isTalkBackActive: Boolean
        get() = accessibilityManager?.isTouchExplorationEnabled == true

    /**
     * Announces text to TalkBack ONLY IF Bhashini TTS audio is not actively playing.
     * Prevents dual audio collision where TalkBack and Bhashini speak simultaneously.
     */
    fun announceAccessibilityText(message: String, isBhashiniAudioPlaying: Boolean) {
        if (message.isBlank()) return

        // If Bhashini TTS is actively speaking, SUPPRESS redundant accessibility announcements
        if (isBhashiniAudioPlaying) {
            Log.d(TAG, "Suppressed TalkBack announcement because Bhashini TTS is active: '$message'")
            return
        }

        if (isTalkBackActive) {
            try {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    className = javaClass.name
                    packageName = context.packageName
                    getText().add(message)
                }
                accessibilityManager?.sendAccessibilityEvent(event)
                Log.d(TAG, "Sent TalkBack announcement: '$message'")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to send accessibility event", t)
            }
        }
    }
}
