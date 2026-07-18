package com.idumet.frank.service

import kotlin.math.max
import kotlin.math.sqrt

class AdaptiveSilenceDetector(
    private val calibrationDurationMs: Long = 500L,
    private val requiredSpeechDurationMs: Long = 350L,
    private val endSilenceDurationMs: Long = 1_600L,
    private val noSpeechTimeoutMs: Long = 8_000L,
    private val maximumRecordingDurationMs: Long = 15_000L,
    private val thresholdMultiplier: Double = 2.2,
    private val minimumThreshold: Double = 500.0,
    private val thresholdMargin: Double = 250.0,
    private val onSpeechStarted: () -> Unit = {},
    private val onFinished: (FinishReason) -> Unit
) {

    enum class FinishReason {
        SILENCE_AFTER_SPEECH,
        NO_SPEECH_TIMEOUT,
        MAXIMUM_DURATION
    }

    private var recordingStartedAt = 0L
    private var calibrationFinishedAt = 0L

    private var ambientLevelTotal = 0.0
    private var ambientSampleCount = 0

    private var noiseFloor = minimumThreshold
    private var speechThreshold = minimumThreshold

    private var candidateSpeechStartedAt: Long? = null
    private var lastSpeechAt: Long? = null

    private var hasDetectedSpeech = false
    private var hasFinished = false

    fun start() {
        val now = System.currentTimeMillis()

        recordingStartedAt = now
        calibrationFinishedAt = now + calibrationDurationMs

        ambientLevelTotal = 0.0
        ambientSampleCount = 0

        noiseFloor = minimumThreshold
        speechThreshold = minimumThreshold

        candidateSpeechStartedAt = null
        lastSpeechAt = null

        hasDetectedSpeech = false
        hasFinished = false
    }

    fun process(samples: ShortArray, sampleCount: Int) {
        if (hasFinished || sampleCount <= 0) {
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - recordingStartedAt
        val rms = calculateRms(samples, sampleCount)

        if (elapsed >= maximumRecordingDurationMs) {
            finish(FinishReason.MAXIMUM_DURATION)
            return
        }

        if (now < calibrationFinishedAt) {
            updateCalibration(rms)
            return
        }

        if (ambientSampleCount > 0) {
            calculateThreshold()
        }

        if (!hasDetectedSpeech) {
            detectInitialSpeech(
                rms = rms,
                now = now
            )

            if (
                !hasDetectedSpeech &&
                elapsed >= noSpeechTimeoutMs
            ) {
                finish(FinishReason.NO_SPEECH_TIMEOUT)
            }

            return
        }

        detectEndOfSpeech(
            rms = rms,
            now = now
        )
    }

    fun reset() {
        hasFinished = true
    }

    fun currentNoiseFloor(): Double {
        return noiseFloor
    }

    fun currentSpeechThreshold(): Double {
        return speechThreshold
    }

    private fun updateCalibration(rms: Double) {
        ambientLevelTotal += rms
        ambientSampleCount += 1
    }

    private fun calculateThreshold() {
        noiseFloor =
            ambientLevelTotal / ambientSampleCount

        speechThreshold = max(
            minimumThreshold,
            (noiseFloor * thresholdMultiplier) +
                    thresholdMargin
        )
    }

    private fun detectInitialSpeech(
        rms: Double,
        now: Long
    ) {
        if (rms >= speechThreshold) {
            val candidateStart =
                candidateSpeechStartedAt

            if (candidateStart == null) {
                candidateSpeechStartedAt = now
                return
            }

            if (
                now - candidateStart >=
                requiredSpeechDurationMs
            ) {
                hasDetectedSpeech = true
                lastSpeechAt = now
                candidateSpeechStartedAt = null
                onSpeechStarted()
            }

            return
        }

        candidateSpeechStartedAt = null

        // Slowly adapt while waiting for the user to speak.
        adaptNoiseFloor(rms)
    }

    private fun detectEndOfSpeech(
        rms: Double,
        now: Long
    ) {
        if (rms >= speechThreshold) {
            lastSpeechAt = now
            return
        }

        val lastSpeech = lastSpeechAt ?: now

        if (
            now - lastSpeech >=
            endSilenceDurationMs
        ) {
            finish(
                FinishReason.SILENCE_AFTER_SPEECH
            )
        }
    }

    private fun adaptNoiseFloor(rms: Double) {
        val smoothing = 0.05

        noiseFloor =
            (noiseFloor * (1.0 - smoothing)) +
                    (rms * smoothing)

        speechThreshold = max(
            minimumThreshold,
            (noiseFloor * thresholdMultiplier) +
                    thresholdMargin
        )
    }

    private fun calculateRms(
        samples: ShortArray,
        sampleCount: Int
    ): Double {
        var sumOfSquares = 0.0

        for (index in 0 until sampleCount) {
            val sample = samples[index].toDouble()
            sumOfSquares += sample * sample
        }

        return sqrt(
            sumOfSquares / sampleCount
        )
    }

    private fun finish(reason: FinishReason) {
        if (hasFinished) {
            return
        }

        hasFinished = true
        onFinished(reason)
    }
}