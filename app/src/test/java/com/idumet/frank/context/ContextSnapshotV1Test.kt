package com.idumet.frank.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ContextSnapshotV1Test {
    @Test fun validatesKnownUnknownDeniedAndStaleSignals() {
        val now = 1_788_000_000_000L
        val snapshot = snapshot(now)
        ContextSnapshotValidatorV1.validate(snapshot, now)
        assertEquals("known", snapshot.location.status)
        assertEquals("denied", snapshot.permissions.location.status)
        assertEquals("unknown", snapshot.movement.status)
        assertEquals("stale", snapshot.evidence.status)
    }

    @Test fun rejectsExpiredSnapshots() {
        val now = 1_788_000_000_000L
        assertThrows(IllegalArgumentException::class.java) {
            ContextSnapshotValidatorV1.validate(snapshot(now - 70_000), now)
        }
    }

    @Test fun rejectsFutureAndMalformedSignalChronology() {
        val now = 1_788_000_000_000L
        val future = snapshot(now).copy(
            location = snapshot(now).location.copy(
                observedAt = Instant.ofEpochMilli(now + 20_000).toString()
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            ContextSnapshotValidatorV1.validate(future, now)
        }
        val conflict = snapshot(now).copy(
            movement = ContextSignalV1(
                status = "conflicting", reason = "sensor_disagreement", sources = listOf("gps"),
                provenance = ContextProvenanceV1("android_sensor", Instant.ofEpochMilli(now).toString())
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            ContextSnapshotValidatorV1.validate(conflict, now)
        }
    }

    private fun snapshot(now: Long): ContextSnapshotV1 {
        val observed = Instant.ofEpochMilli(now); val expires = observed.plusSeconds(30)
        val source = ContextProvenanceV1("android_system", observed.toString())
        fun <T> known(value: T) = ContextSignalV1(status = "known", value = value, observedAt = observed.toString(), expiresAt = expires.toString(), provenance = source)
        fun <T> unknown() = ContextSignalV1<T>(status = "unknown", reason = "not_collected", provenance = source)
        fun <T> denied(permission: String) = ContextSignalV1<T>(status = "denied", permission = permission, provenance = source)
        fun <T> stale() = ContextSignalV1<T>(status = "stale", lastObservedAt = observed.minusSeconds(10).toString(), expiredAt = observed.minusSeconds(5).toString(), provenance = source)
        return ContextSnapshotV1(
            snapshotId = "snapshot-test-001", sessionId = "session-test", capturedAt = observed.toString(), expiresAt = expires.toString(),
            time = known(TimeContextV1("Europe/London", 60)),
            permissions = PermissionContextV1(known("granted"), known("granted"), denied("location"), unknown()),
            device = DeviceContextV1(known("online"), known("locked"), known(true), known(75), known(true)),
            app = known(AppContextV1("listening", true)),
            location = known(LocationContextV1(51.5, -0.1, 15.0)), movement = unknown(), evidence = stale()
        )
    }
}
