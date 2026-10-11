package com.sarvaai.app.service.wakeword

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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sarvaai.app.MainActivity
import java.util.ArrayList

/**
 * Continuous Hotword Foreground Service.
 * Implements persistent background listening for 'Hey Svara' and handles voice shutdown commands.
 */
class SvaraWakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isListeningActive: Boolean = false
    private var isCurrentlyRecognizing: Boolean = false
    private var errorRetryCount: Int = 0

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
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopWakeWordListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SERVICE, null -> {
                startAsForeground()
                startWakeWordListening()
            }
        }

        return START_STICKY
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous background listening for Hey Svara voice trigger"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        // Quick Action: Open Svara App
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START_LISTENING, true)
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_btn_speak_now, "🎙️ Open Svara", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 Stop", stopPendingIntent)
            .build()
    }

    private fun startWakeWordListening() {
        if (isListeningActive) return
        isListeningActive = true
        errorRetryCount = 0

        mainHandler.post {
            initAndStartSpeechRecognizer()
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
        if (!isListeningActive) return

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
        if (!isListeningActive || isCurrentlyRecognizing) return

        val recognizer = speechRecognizer ?: return
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-IN", "hi-IN", "te-IN"))
        }

        try {
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
                if (!isListeningActive) return

                val delay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                    else -> 1000L
                }

                errorRetryCount++
                if (errorRetryCount > 5) {
                    errorRetryCount = 0
                    mainHandler.postDelayed({
                        if (isListeningActive) {
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

                if (isListeningActive) {
                    scheduleRestartListening(LOOP_REARM_DELAY_MS)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches: ArrayList<String>? = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (text in matches) {
                        if (WakeWordDetector.isWakeWord(text)) {
                            Log.i(TAG, "Wake word detected in partial results: '$text'")
                            triggerWakeWordDetected()
                            break
                        } else if (WakeWordDetector.isCloseCommand(text)) {
                            Log.i(TAG, "Close command detected in partial results: '$text'")
                            triggerCloseCommandDetected()
                            break
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processRecognizedHypotheses(matches: List<String>) {
        for (text in matches) {
            if (WakeWordDetector.isWakeWord(text)) {
                Log.i(TAG, "Wake word matched onResults: '$text'")
                triggerWakeWordDetected()
                return
            }
            if (WakeWordDetector.isCloseCommand(text)) {
                Log.i(TAG, "Close command matched onResults: '$text'")
                triggerCloseCommandDetected()
                return
            }
        }
    }

    private fun triggerWakeWordDetected() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START_LISTENING, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            pendingIntent.send()
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent send failed, launching direct startActivity: ${e.message}")
            startActivity(launchIntent)
        }
    }

    private fun triggerCloseCommandDetected() {
        Log.i(TAG, "Broadcasting ACTION_CLOSE_APP to close all activities")
        val closeIntent = Intent(ACTION_CLOSE_APP).apply {
            setPackage(packageName)
        }
        sendBroadcast(closeIntent)
    }

    private fun scheduleRestartListening(delayMs: Long) {
        if (!isListeningActive) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isListeningActive) {
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
    }

    companion object {
        private const val TAG = "SvaraWakeWordService"

        const val CHANNEL_ID = "svara_wakeword_channel"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE_OPEN = 101
        private const val REQUEST_CODE_STOP = 102

        const val ACTION_START_SERVICE = "com.sarvaai.app.action.START_WAKE_WORD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sarvaai.app.action.STOP_WAKE_WORD_SERVICE"
        const val ACTION_CLOSE_APP = "com.sarvaai.app.ACTION_CLOSE_APP"
        const val EXTRA_AUTO_START_LISTENING = "EXTRA_AUTO_START_LISTENING"

        private const val RETRY_DELAY_MS = 600L
        private const val LOOP_REARM_DELAY_MS = 250L

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
