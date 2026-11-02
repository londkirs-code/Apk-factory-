package com.londkirs.monopoly.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class GameFeedback(context: Context) {
    private val appContext = context.applicationContext

    fun dice(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) {
            runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, 65).apply {
                    startTone(ToneGenerator.TONE_PROP_BEEP, 110)
                    release()
                }
            }
        }
        if (vibrationEnabled) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                    appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
