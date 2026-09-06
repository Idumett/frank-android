package com.idumet.frank.service

import kotlin.math.max
import kotlin.math.sqrt

class AdaptiveSilenceDetector(
    private val calibrationDurationMs: Long = 500L,
    private val requiredSpeechDurationMs: Long = 100L,
    private val endSilenceDurationMs: Long = 1_600L,
    private val noSpeechTimeoutMs: Long = 8_000L,
    private val maximumRecordingDurationMs: Long = 15_000L,
    private val thresholdMultiplier: Double = 1.5,
    private val minimumThreshold: Double = 80.0,
    private val thresholdMargin: Double = 40.0,
    private val onSpeechStarted: () -> Unit = {},
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
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

    private var noiseFloor = 0.0
    private var speechThreshold = minimumThreshold

    private var candidateSpeechStartedAt: Long? = null
    private var lastSpeechAt: Long? = null

    private var peakRms = 0.0
    private var hasDetectedSpeech = false
    private var hasFinished = false

    fun start() {
        val now = clockMs()

        recordingStartedAt = now
        calibrationFinishedAt = now + calibrationDurationMs

        ambientLevelTotal = 0.0
        ambientSampleCount = 0

        noiseFloor = 0.0
        speechThreshold = minimumThreshold

        candidateSpeechStartedAt = null
        lastSpeechAt = null

        peakRms = 0.0
        hasDetectedSpeech = false
        hasFinished = false
    }

    fun process(samples: ShortArray, sampleCount: Int) {
        if (hasFinished || sampleCount <= 0) {
            return
        }

        val now = clockMs()
        val elapsed = now - recordingStartedAt
        val rms = calculateRms(samples, sampleCount)
        peakRms = max(peakRms, rms)

        if (elapsed >= maximumRecordingDurationMs) {
            finish(FinishReason.MAXIMUM_DURATION)
            return
        }

        // Speech can begin immediately. Only quiet samples belong in the
        // ambient estimate; treating the opening words as noise hides speech.
        if (now < calibrationFinishedAt && rms < minimumThreshold) {
            updateCalibration(rms)
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

    /** Signal level only; no speech content is stored in diagnostics. */
    fun currentPeakRms(): Double = peakRms

    fun currentNoiseFloor(): Double {
        return noiseFloor
    }

    fun currentSpeechThreshold(): Double {
        return speechThreshold
    }

    /**
     * The speech detector should decide when to stop, not prevent usable audio
     * from reaching STT. A small non-zero peak is enough to distinguish a live
     * microphone from the all-zero stream produced when emulator input is absent.
     */
    fun hasCapturedAudioSignal(minimumPeakRms: Double = 24.0): Boolean {
        return peakRms >= minimumPeakRms
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
