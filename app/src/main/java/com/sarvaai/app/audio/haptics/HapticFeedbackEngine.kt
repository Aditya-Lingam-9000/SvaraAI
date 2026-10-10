package com.sarvaai.app.audio.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticPattern {
    CLICK,
    DOUBLE_CLICK,
    CONFIRMATION,
    ERROR_ALERT,
    CAMERA_STABLE,
    SPEECH_TURN_CUE
}

class HapticFeedbackEngine(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun trigger(pattern: HapticPattern) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (pattern) {
                HapticPattern.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticPattern.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticPattern.CAMERA_STABLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticPattern.CONFIRMATION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 80)
                            .compose()
                    } else {
                        VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 255), -1)
                    }
                }
                HapticPattern.ERROR_ALERT -> {
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), intArrayOf(0, 255, 0, 255), -1)
                }
                HapticPattern.SPEECH_TURN_CUE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                            .compose()
                    } else {
                        VibrationEffect.createOneShot(50, 180)
                    }
                }
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(50)
        }
    }
}
