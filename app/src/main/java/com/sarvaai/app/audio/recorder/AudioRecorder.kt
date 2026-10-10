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
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 320 samples
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 640 bytes
    }

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

        if (_isRecording.value) {
            stopRecording()
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError("AudioRecord hardware buffer initialization failed")
            return
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(BYTES_PER_FRAME * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_RECOGNITION fails on specific hardware
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Failed to initialize AudioRecord with 16kHz PCM")
                    return
                }
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            vad.reset()

            recordingJob = coroutineScope.launch {
                val pcmBuffer = ByteArray(BYTES_PER_FRAME)
                val utteranceStream = ByteArrayOutputStream()
                var speechCaptured = false

                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(pcmBuffer, 0, BYTES_PER_FRAME) ?: -1
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
                                speechCaptured = true
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
                                }
                            }
                        }
                    } else if (readBytes < 0) {
                        Log.e("AudioRecorder", "AudioRecord read error: $readBytes")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Exception starting audio recorder", e)
            onError(e.localizedMessage ?: "Audio recording failure")
            stopRecording()
        }
    }

    fun stopRecording(): ByteArray? {
        _isRecording.value = false
        _vadStatus.value = VadStatus.SILENCE
        _audioAmplitude.value = 0f

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
        return null
    }

    fun release() {
        stopRecording()
        coroutineScope.cancel()
    }
}
