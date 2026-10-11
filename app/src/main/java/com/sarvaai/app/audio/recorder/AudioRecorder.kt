package com.sarvaai.app.audio.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.sarvaai.app.audio.vad.EnergyVad
import com.sarvaai.app.audio.vad.VadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class AudioRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 320 samples
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 640 bytes
    }

    private val lock = Any()
    @Volatile private var isRecordingActive = false
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val vad = EnergyVad(SAMPLE_RATE, FRAME_DURATION_MS)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _vadStatus = MutableStateFlow(VadStatus.SILENCE)
    val vadStatus: StateFlow<VadStatus> = _vadStatus.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    fun setSilenceTimeout(timeoutMs: Long) {
        vad.setSilenceTimeout(timeoutMs)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        onUtteranceComplete: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission not granted")
            return
        }

        synchronized(lock) {
            if (isRecordingActive || audioRecord != null) {
                stopRecordingInternal()
            }

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onError("AudioRecord hardware buffer initialization failed")
                return
            }

            val bufferSize = (minBufferSize * 2).coerceAtLeast(BYTES_PER_FRAME * 4)

            try {
                var record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    // Fallback to standard MIC source if VOICE_RECOGNITION fails on specific hardware
                    try { record.release() } catch (_: Throwable) {}
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        try { record.release() } catch (_: Throwable) {}
                        onError("Failed to initialize AudioRecord with 16kHz PCM")
                        return
                    }
                }

                record.startRecording()
                audioRecord = record
                isRecordingActive = true
                _isRecording.value = true
                vad.reset()

                recordingJob = coroutineScope.launch(Dispatchers.IO) {
                    val pcmBuffer = ByteArray(BYTES_PER_FRAME)
                    val utteranceStream = ByteArrayOutputStream()
                    val preRollCapacity = 20 // 20 frames * 20ms = 400ms pre-roll buffer
                    val preRollBuffer = ArrayDeque<ByteArray>(preRollCapacity)
                    var speechCaptured = false

                    try {
                        while (isActive && isRecordingActive) {
                            val currentRecord = synchronized(lock) { audioRecord } ?: break
                            if (currentRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                            val readBytes = try {
                                currentRecord.read(pcmBuffer, 0, BYTES_PER_FRAME)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Exception during AudioRecord.read", t)
                                -1
                            }

                            if (readBytes > 0) {
                                val status = vad.processFrame(pcmBuffer, readBytes)
                                _vadStatus.value = status

                                // Calculate normalized amplitude for UI visualizer
                                var maxSample = 0
                                for (i in 0 until readBytes / 2) {
                                    val sample = Math.abs((pcmBuffer[i * 2 + 1].toInt() shl 8) or (pcmBuffer[i * 2].toInt() and 0xFF))
                                    if (sample > maxSample) maxSample = sample
                                }
                                _audioAmplitude.value = (maxSample / 32767f).coerceIn(0f, 1f)

                                when (status) {
                                    VadStatus.SPEECH_ACTIVE -> {
                                        if (!speechCaptured) {
                                            speechCaptured = true
                                            // Flush all pre-roll frames so no initial syllables are clipped
                                            while (preRollBuffer.isNotEmpty()) {
                                                val preFrame = preRollBuffer.removeFirst()
                                                utteranceStream.write(preFrame)
                                            }
                                        }
                                        utteranceStream.write(pcmBuffer, 0, readBytes)
                                    }
                                    VadStatus.SPEECH_ENDED -> {
                                        if (speechCaptured && utteranceStream.size() > 0) {
                                            val rawPcm = utteranceStream.toByteArray()
                                            val wavBytes = WavEncoder.encodeToWav(rawPcm, SAMPLE_RATE)
                                            withContext(Dispatchers.Main) {
                                                onUtteranceComplete(wavBytes)
                                            }
                                        }
                                        stopRecording()
                                        break
                                    }
                                    VadStatus.CALIBRATING, VadStatus.SILENCE -> {
                                        if (speechCaptured) {
                                            utteranceStream.write(pcmBuffer, 0, readBytes)
                                        } else {
                                            if (preRollBuffer.size >= preRollCapacity) {
                                                preRollBuffer.removeFirst()
                                            }
                                            preRollBuffer.addLast(pcmBuffer.copyOf(readBytes))
                                        }
                                    }
                                }
                            } else if (readBytes < 0) {
                                Log.w(TAG, "AudioRecord read error code: $readBytes")
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Audio recording loop error", t)
                    } finally {
                        try { utteranceStream.close() } catch (_: Throwable) {}
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception starting audio recorder", e)
                onError(e.localizedMessage ?: "Audio recording failure")
                stopRecordingInternal()
            }
        }
    }

    private fun stopRecordingInternal(): ByteArray? {
        isRecordingActive = false
        _isRecording.value = false
        _vadStatus.value = VadStatus.SILENCE
        _audioAmplitude.value = 0f

        val job = recordingJob
        recordingJob = null
        job?.cancel()

        try {
            audioRecord?.let { record ->
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        try { record.stop() } catch (_: Throwable) {}
                    }
                    try { record.release() } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping AudioRecord", t)
        } finally {
            audioRecord = null
        }
        return null
    }

    fun stopRecording(): ByteArray? {
        synchronized(lock) {
            return stopRecordingInternal()
        }
    }

    fun release() {
        stopRecording()
        try {
            coroutineScope.cancel()
        } catch (t: Throwable) {
            Log.w(TAG, "Error cancelling coroutineScope", t)
        }
    }
}
