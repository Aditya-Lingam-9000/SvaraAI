package com.sarvaai.app.service.wakeword

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sarvaai.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.ArrayList

/**
 * Continuous Hotword Foreground Service.
 * Implements persistent background listening for 'Hey Svara' using Android SpeechRecognizer.
 * Coordinates exclusive microphone access with MainActivity to prevent mic contention and audio feedback loops.
 */
class SvaraWakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isListeningActive: Boolean = false
    private var isCurrentlyRecognizing: Boolean = false
    private var errorRetryCount: Int = 0
    private var lastWakeTriggerTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SvaraWakeWordService onCreate")
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SarvaAI:WakeWordServiceWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action (isAppInForeground=$isAppInForeground)")

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopWakeWordListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_LISTENING -> {
                Log.i(TAG, "Pausing wake word listening (app is in foreground)")
                pauseWakeWordListening()
            }
            ACTION_RESUME_LISTENING -> {
                Log.i(TAG, "Resuming wake word listening (isAppInForeground=$isAppInForeground, isServiceRunning=$isServiceRunning)")
                if (!isAppInForeground && isServiceRunning) {
                    startWakeWordListening()
                }
            }
            ACTION_START_SERVICE, null -> {
                startAsForeground()
                if (!isAppInForeground) {
                    startWakeWordListening()
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "SvaraWakeWordService onTaskRemoved - scheduling alarm manager revival")
        try {
            val restartServiceIntent = Intent(applicationContext, SvaraWakeWordService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            val restartServicePendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000L,
                restartServicePendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule service restart onTaskRemoved: ${e.message}", e)
        }
    }

    private fun startAsForeground() {
        try {
            wakeLock?.acquire(10 * 60 * 1000L /* 10 mins */)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire error: ${e.message}")
        }

        val notification = buildPersistentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification: ${e.message}", e)
        }
        isServiceRunning = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Svara Hotword Listener",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Continuous background listening for Hey Svara voice trigger"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        // Quick Action: Open Svara App
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_AUTO_START_LISTENING, true)
            putExtra(WakeWordDetector.EXTRA_AUTO_START_LISTENING, true)
            putExtra("EXTRA_AUTO_START_LISTENING", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Action: Stop Service
        val stopIntent = Intent(this, SvaraWakeWordService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Svara Voice Trigger Active")
            .setContentText("Listening for \"Hey Svara\"...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_btn_speak_now, "🎙️ Open Svara", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 Stop", stopPendingIntent)
            .build()
    }

    private fun startWakeWordListening() {
        if (!IS_WAKE_WORD_ENABLED) {
            Log.d(TAG, "startWakeWordListening skipped: IS_WAKE_WORD_ENABLED is false")
            return
        }
        if (isListeningActive || isAppInForeground) {
            Log.d(TAG, "startWakeWordListening skipped: isListeningActive=$isListeningActive, isAppInForeground=$isAppInForeground")
            return
        }
        isListeningActive = true
        errorRetryCount = 0

        mainHandler.post {
            initAndStartSpeechRecognizer()
        }
    }

    private fun pauseWakeWordListening() {
        Log.d(TAG, "pauseWakeWordListening called")
        isListeningActive = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            safeDestroyRecognizer()
        }
    }

    private fun stopWakeWordListening() {
        isListeningActive = false
        isServiceRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            safeDestroyRecognizer()
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release error: ${e.message}")
        }
    }

    private fun initAndStartSpeechRecognizer() {
        if (!isListeningActive || isAppInForeground) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device")
            return
        }

        try {
            safeDestroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
            beginListeningLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}", e)
            scheduleRestartListening(RETRY_DELAY_MS)
        }
    }

    private fun beginListeningLoop() {
        if (!isListeningActive || isCurrentlyRecognizing || isAppInForeground) return

        val recognizer = speechRecognizer ?: return
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-IN", "hi-IN", "te-IN"))
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "te-IN", "en-IN"))
        }

        try {
            try {
                recognizer.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling recognizer before start: ${e.message}")
            }
            isCurrentlyRecognizing = true
            recognizer.startListening(recognizerIntent)
            Log.d(TAG, "SpeechRecognizer continuous loop started")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting speech listening: ${e.message}", e)
            isCurrentlyRecognizing = false
            scheduleRestartListening(RETRY_DELAY_MS)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isCurrentlyRecognizing = true
                errorRetryCount = 0
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech beginning detected")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isCurrentlyRecognizing = false
            }

            override fun onError(error: Int) {
                Log.d(TAG, "SpeechRecognizer onError: $error")
                isCurrentlyRecognizing = false
                try {
                    speechRecognizer?.cancel()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling speech recognizer on error: ${e.message}")
                }
                if (!isListeningActive || isAppInForeground) return

                val delay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                    else -> 1000L
                }

                errorRetryCount++
                if (errorRetryCount > 3 || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    errorRetryCount = 0
                    mainHandler.postDelayed({
                        if (isListeningActive && !isAppInForeground) {
                            initAndStartSpeechRecognizer()
                        }
                    }, delay)
                } else {
                    scheduleRestartListening(delay)
                }
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyRecognizing = false
                val matches: ArrayList<String>? = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "Speech recognition onResults matches: $matches")
                if (matches != null) {
                    processRecognizedHypotheses(matches)
                }

                if (isListeningActive && !isAppInForeground) {
                    scheduleRestartListening(LOOP_REARM_DELAY_MS)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isListeningActive || isAppInForeground) return
                val matches: ArrayList<String>? = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (text in matches) {
                        if (WakeWordDetector.isWakeWord(text)) {
                            Log.i(TAG, "Wake word detected in partial results: '$text'")
                            triggerWakeWordDetected()
                            break
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processRecognizedHypotheses(matches: List<String>) {
        if (!isListeningActive || isAppInForeground) return
        for (text in matches) {
            if (WakeWordDetector.isWakeWord(text)) {
                Log.i(TAG, "Wake word matched onResults: '$text'")
                triggerWakeWordDetected()
                return
            }
        }
    }

    private fun triggerWakeWordDetected() {
        if (!IS_WAKE_WORD_ENABLED) {
            Log.d(TAG, "triggerWakeWordDetected skipped: IS_WAKE_WORD_ENABLED is false")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeTriggerTime < 8000L) {
            Log.w(TAG, "Wake word trigger ignored: 8-second cooldown active (${now - lastWakeTriggerTime}ms < 8000ms)")
            return
        }
        lastWakeTriggerTime = now

        Log.i(TAG, "Triggering wake word detected: bringing MainActivity to foreground and starting listening")

        // 1. Immediate tactile vibration feedback ONLY (NO speaker sound to prevent acoustic loop!)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, 255))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wake word vibrator error: ${e.message}")
        }

        // Pause listening immediately so MainActivity can take over exclusive mic access
        pauseWakeWordListening()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_AUTO_START_LISTENING, true)
            putExtra(WakeWordDetector.EXTRA_AUTO_START_LISTENING, true)
            putExtra("EXTRA_AUTO_START_LISTENING", true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create high-priority full-screen intent notification
        val fullScreenNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Svara Voice Triggered")
            .setContentText("Listening for your voice...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            notificationManager?.notify(WAKE_NOTIFICATION_ID, fullScreenNotification)
        } catch (e: Exception) {
            Log.w(TAG, "Notification notify error: ${e.message}")
        }

        try {
            fullScreenPendingIntent.send()
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent send failed, launching direct startActivity: ${e.message}")
            try {
                startActivity(launchIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "startActivity failed: ${ex.message}", ex)
            }
        }
    }

    private fun scheduleRestartListening(delayMs: Long) {
        if (!isListeningActive || isAppInForeground) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isListeningActive && !isAppInForeground) {
                beginListeningLoop()
            }
        }, delayMs)
    }

    private fun safeDestroyRecognizer() {
        try {
            isCurrentlyRecognizing = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error while destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "SvaraWakeWordService onDestroy")
        stopWakeWordListening()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "SvaraWakeWordService"

        const val IS_WAKE_WORD_ENABLED = false // Temporarily disabled per user request

        const val CHANNEL_ID = "svara_wakeword_channel"
        const val NOTIFICATION_ID = 2001
        const val WAKE_NOTIFICATION_ID = 2002
        private const val REQUEST_CODE_OPEN = 101
        private const val REQUEST_CODE_STOP = 102

        const val ACTION_START_SERVICE = "com.sarvaai.app.action.START_WAKE_WORD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sarvaai.app.action.STOP_WAKE_WORD_SERVICE"
        const val ACTION_PAUSE_LISTENING = "com.sarvaai.app.action.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.sarvaai.app.action.RESUME_LISTENING"
        const val ACTION_CLOSE_APP = "com.sarvaai.app.ACTION_CLOSE_APP"
        const val EXTRA_AUTO_START_LISTENING = "EXTRA_AUTO_START_LISTENING"

        private const val RETRY_DELAY_MS = 600L
        private const val LOOP_REARM_DELAY_MS = 250L

        @Volatile
        var isAppInForeground: Boolean = false

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context, SvaraWakeWordService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SvaraWakeWordService: ${e.message}", e)
            }
        }

        fun pauseListening(context: Context) {
            try {
                val intent = Intent(context, SvaraWakeWordService::class.java).apply {
                    action = ACTION_PAUSE_LISTENING
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause SvaraWakeWordService: ${e.message}", e)
            }
        }

        fun resumeListening(context: Context) {
            try {
                val intent = Intent(context, SvaraWakeWordService::class.java).apply {
                    action = ACTION_RESUME_LISTENING
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume SvaraWakeWordService: ${e.message}", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, SvaraWakeWordService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SvaraWakeWordService: ${e.message}", e)
            }
        }
    }
}
