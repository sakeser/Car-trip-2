package com.cartrip.analyzer.record

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile cues for recording state, so the driver can FEEL what the app is doing without looking at the
 * phone (and so field tests don't need a screen check). Four distinct patterns:
 *
 *  - [armed]:            light double tick  — an auto-record provisional trip started; motion not yet confirmed.
 *  - [recordingStarted]: one firm buzz      — the trip is actually recording (manual start, or motion confirmed).
 *  - [stopped]:          two firm buzzes    — a real trip ended and was saved.
 *  - [disarmed]:         one soft tick       — a provisional was discarded (it wasn't a drive).
 *
 * All no-ops if the device has no vibrator. Safe to call from the recording service (background).
 */
object Haptics {

    fun armed(context: Context) =
        play(context, longArrayOf(0, 45, 90, 45), intArrayOf(0, 120, 0, 120))

    fun recordingStarted(context: Context) =
        play(context, longArrayOf(0, 230), intArrayOf(0, 255))

    fun stopped(context: Context) =
        play(context, longArrayOf(0, 170, 110, 170), intArrayOf(0, 255, 0, 255))

    fun disarmed(context: Context) =
        play(context, longArrayOf(0, 35), intArrayOf(0, 90))

    private fun play(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator(context) ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1) // pattern only on devices without amplitude control
            }
            vib.vibrate(effect)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
