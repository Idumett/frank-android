package com.idumet.frank

import com.idumet.frank.service.AdaptiveSilenceDetector
import com.idumet.frank.service.AdaptiveSilenceDetector.FinishReason
import org.junit.Assert.*
import org.junit.Test

class AdaptiveSilenceDetectorTest {
    private class Recording {
        var now = 0L
        var starts = 0
        val finishes = mutableListOf<FinishReason>()
        val detector = AdaptiveSilenceDetector(clockMs = { now },
            onSpeechStarted = { starts++ }, onFinished = { finishes.add(it) }).apply { start() }
        fun samples(durationMs: Int, amplitude: Int) {
            repeat(durationMs / 50) {
                now += 50
                detector.process(ShortArray(800) { amplitude.toShort() }, 800)
            }
        }
    }

    @Test fun silenceTimesOutOnceWithoutSpeech() {
        val r = Recording()
        r.samples(10_000, 0)
        assertEquals(0, r.starts)
        assertEquals(listOf(FinishReason.NO_SPEECH_TIMEOUT), r.finishes)
    }

    @Test fun speakingImmediatelyIsNotCalibratedAway() {
        val r = Recording()
        r.samples(1_000, 3_000)
        r.samples(1_600, 0)
        assertEquals(1, r.starts)
        assertEquals(listOf(FinishReason.SILENCE_AFTER_SPEECH), r.finishes)
    }

    @Test fun aShortAnswerAfterSilenceIsRecognized() {
        val r = Recording()
        r.samples(1_000, 50)
        r.samples(250, 1_000)
        r.samples(1_600, 50)
        assertEquals(listOf(FinishReason.SILENCE_AFTER_SPEECH), r.finishes)
    }

    @Test fun aBriefNoiseDoesNotCountAsSpeech() {
        val r = Recording()
        r.samples(1_000, 0)
        r.samples(50, 3_000)
        r.samples(8_000, 0)
        assertEquals(0, r.starts)
        assertEquals(listOf(FinishReason.NO_SPEECH_TIMEOUT), r.finishes)
    }

    @Test fun noiseEstimateKeepsAdaptingAfterCalibration() {
        val r = Recording()
        r.samples(500, 300)
        val calibrated = r.detector.currentNoiseFloor()
        r.samples(2_000, 20)
        assertTrue(r.detector.currentNoiseFloor() < calibrated / 2)
        r.samples(250, 600)
        r.samples(1_600, 20)
        assertEquals(listOf(FinishReason.SILENCE_AFTER_SPEECH), r.finishes)
    }

    @Test fun aPauseWithinSpeechDoesNotEndTheRecording() {
        val r = Recording()
        r.samples(500, 1_000)
        r.samples(800, 0)
        assertTrue(r.finishes.isEmpty())
        r.samples(500, 1_000)
        r.samples(1_600, 0)
        assertEquals(listOf(FinishReason.SILENCE_AFTER_SPEECH), r.finishes)
    }

    @Test fun continuousSpeechStillHasAMaximumDuration() {
        val r = Recording()
        r.samples(16_000, 1_000)
        assertEquals(listOf(FinishReason.MAXIMUM_DURATION), r.finishes)
    }

    @Test fun resetSuppressesCallbacksAndStartCreatesANewRecording() {
        val r = Recording()
        r.detector.reset()
        r.samples(10_000, 0)
        assertTrue(r.finishes.isEmpty())
        r.detector.start()
        r.samples(500, 1_000)
        r.samples(1_600, 0)
        assertEquals(listOf(FinishReason.SILENCE_AFTER_SPEECH), r.finishes)
    }
}
