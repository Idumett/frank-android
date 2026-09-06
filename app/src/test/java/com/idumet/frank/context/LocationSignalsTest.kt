package com.idumet.frank.context

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LocationSignalsTest {
    private val now = Instant.parse("2026-09-06T10:00:00Z")
    private fun fix(ageSeconds: Long = 0, accuracy: Double? = 15.0, speed: Double? = 1.0) =
        LocationFix(now.minusSeconds(ageSeconds).toEpochMilli(), 51.5, -0.1, accuracy, speed = speed)

    @Test fun currentFixCarriesActualObservationAndExpiry() {
        val (location, movement) = locationSignals(fix(5), now)
        assertEquals("known", location.status)
        assertEquals(now.minusSeconds(5).toString(), location.observedAt)
        assertEquals(now.plusSeconds(25).toString(), location.expiresAt)
        assertEquals("walking", movement.value?.state)
    }
    @Test fun staleFixIsNeverRelabelledAsCurrent() {
        assertEquals("stale", locationSignals(fix(30), now).first.status)
        assertEquals("stale", locationSignals(fix(600), now).first.status)
    }
    @Test fun movementExpiresBeforeLocation() {
        val (location, movement) = locationSignals(fix(20), now)
        assertEquals("known", location.status)
        assertEquals("stale", movement.status)
    }
    @Test fun approximateLocationRemainsUsableWithHonestAccuracy() {
        val (location, _) = locationSignals(fix(accuracy = 1500.0), now)
        assertEquals("known", location.status)
        assertEquals(1500.0, location.value?.accuracyMeters)
    }
    @Test fun invalidSensorsDoNotBecomeKnownFacts() {
        for (accuracy in listOf(null, 0.0, Double.NaN, 10001.0)) {
            assertEquals("conflicting", locationSignals(fix(accuracy = accuracy), now).first.status)
        }
        assertEquals("conflicting", locationSignals(fix(-11), now).first.status)
        assertEquals("unknown", locationSignals(fix(speed = null), now).second.status)
    }
}
