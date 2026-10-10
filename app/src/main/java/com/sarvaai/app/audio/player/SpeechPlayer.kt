package com.sarvaai.app.audio.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class SpeechPlayer(private val context: Context) {
    companion object {
        private const val TAG = "SpeechPlayer"
    }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
    }

    private val tempAudioDir = File(context.cacheDir, "speech_audio_chunks").apply { mkdirs() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSpeed: Float = 1.0f

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentChunkIndex = MutableStateFlow(0)
    val currentChunkIndex: StateFlow<Int> = _currentChunkIndex.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackCompleted: (() -> Unit)? = null
    var onChunkTransition: ((Int) -> Unit)? = null

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (_isPlaying.value) {
                _currentPositionMs.value = exoPlayer.currentPosition
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                mainHandler.postDelayed(this, 50)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val isNowPlaying = (playbackState == Player.STATE_READY && exoPlayer.playWhenReady)
            _isPlaying.value = isNowPlaying

            if (isNowPlaying) {
                mainHandler.post(progressUpdater)
                onPlaybackStarted?.invoke()
            } else if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                mainHandler.removeCallbacks(progressUpdater)
                onPlaybackCompleted?.invoke()
            } else if (playbackState == Player.STATE_IDLE) {
                _isPlaying.value = false
                mainHandler.removeCallbacks(progressUpdater)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = exoPlayer.currentMediaItemIndex
            _currentChunkIndex.value = idx
            onChunkTransition?.invoke(idx)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "ExoPlayer error occurred: ${error.message}", error)
            _isPlaying.value = false
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = currentSpeed
        // Maintain 1.0f pitch to prevent robotic chipmunk distortion
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, 1.0f)
    }

    fun enqueueAudioFile(file: File, clearQueue: Boolean = false) {
        try {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            if (clearQueue) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                exoPlayer.addMediaItem(mediaItem)
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enqueue audio file: ${file.absolutePath}", t)
        }
    }

    fun enqueueAudioUrl(url: String, clearQueue: Boolean = false) {
        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            if (clearQueue) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                exoPlayer.addMediaItem(mediaItem)
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enqueue audio URL: $url", t)
        }
    }

    fun playBase64Wav(base64Wav: String, clearQueue: Boolean = false) {
        try {
            val bytes = Base64.decode(base64Wav, Base64.DEFAULT)
            val tempFile = File(tempAudioDir, "chunk_${System.nanoTime()}.wav")
            FileOutputStream(tempFile).use { it.write(bytes) }
            enqueueAudioFile(tempFile, clearQueue)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode and play base64 audio", t)
        }
    }

    fun stop() {
        try {
            mainHandler.removeCallbacks(progressUpdater)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping player", t)
        }
    }

    fun pause() {
        try {
            exoPlayer.pause()
            _isPlaying.value = false
        } catch (t: Throwable) {
            Log.w(TAG, "Error pausing player", t)
        }
    }

    fun resume() {
        try {
            exoPlayer.play()
            _isPlaying.value = true
        } catch (t: Throwable) {
            Log.w(TAG, "Error resuming player", t)
        }
    }

    fun release() {
        try {
            mainHandler.removeCallbacks(progressUpdater)
            exoPlayer.release()
            _isPlaying.value = false
        } catch (t: Throwable) {
            Log.w(TAG, "Error releasing ExoPlayer", t)
        }
    }
}
