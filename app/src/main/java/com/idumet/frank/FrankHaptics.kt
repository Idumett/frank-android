package com.idumet.frank

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FrankHaptics(
    context: Context
) {
    private val appContext = context.applicationContext
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }

    fun performStateCue(
        state: AssistantState
    ) {
        if (
            !FrankPreferencesStore(appContext).read().hapticsEnabled ||
            !vibrator.hasVibrator()
        ) {
            return
        }

        when (state) {
            AssistantState.Idle ->
                vibrate(
                    timings = longArrayOf(
                        0L,
                        24L
                    ),
                    amplitudes = intArrayOf(
                        0,
                        75
                    )
                )

            /*
             * A deliberate double pulse tells a blind user that Frank
             * has started listening.
             */
            AssistantState.Listening ->
                vibrate(
                    timings = longArrayOf(
                        0L,
                        55L,
                        70L,
                        75L
                    ),
                    amplitudes = intArrayOf(
                        0,
                        150,
                        0,
                        220
                    )
                )

            AssistantState.Thinking ->
                vibrate(
                    timings = longArrayOf(
                        0L,
                        35L
                    ),
                    amplitudes = intArrayOf(
                        0,
                        105
                    )
                )

            AssistantState.Speaking ->
                vibrate(
                    timings = longArrayOf(
                        0L,
                        75L,
                        55L,
                        35L
                    ),
                    amplitudes = intArrayOf(
                        0,
                        215,
                        0,
                        115
                    )
                )

            AssistantState.Error ->
                vibrate(
                    timings = longArrayOf(
                        0L,
                        70L,
                        70L,
                        70L,
                        70L,
                        110L
                    ),
                    amplitudes = intArrayOf(
                        0,
                        210,
                        0,
                        210,
                        0,
                        245
                    )
                )
        }
    }

    private fun vibrate(
        timings: LongArray,
        amplitudes: IntArray
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    timings,
                    amplitudes,
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                timings,
                -1
            )
        }
    }
}
