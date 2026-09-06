package com.idumet.frank.context

import java.time.Instant

internal data class LocationFix(
    val timeMs: Long, val latitude: Double, val longitude: Double,
    val accuracy: Double?, val altitude: Double? = null,
    val speed: Double? = null, val bearing: Double? = null
)

internal fun locationSignals(fix: LocationFix, now: Instant): Pair<ContextSignalV1<LocationContextV1>, ContextSignalV1<MovementContextV1>> {
    val observed = Instant.ofEpochMilli(fix.timeMs)
    val source = ContextProvenanceV1("android_sensor", now.toString())
    fun <T> unknown() = ContextSignalV1<T>(status = "unknown", reason = "unavailable", provenance = source)
    fun <T> stale(seconds: Long) = ContextSignalV1<T>(status = "stale", lastObservedAt = observed.toString(), expiredAt = observed.plusSeconds(seconds).toString(), provenance = source)
    fun <T> conflict(reason: String) = ContextSignalV1<T>(status = "conflicting", reason = reason, sources = listOf("gps", "location_accuracy"), provenance = source)
    fun <T> known(value: T, seconds: Long) = ContextSignalV1(status = "known", value = value, observedAt = observed.toString(), expiresAt = observed.plusSeconds(seconds).toString(), provenance = source)
    if (observed.isAfter(now.plusSeconds(10))) return conflict<LocationContextV1>("invalid_sequence") to unknown()
    val accuracy = fix.accuracy
    if (accuracy == null || !accuracy.isFinite() || accuracy <= 0 || accuracy > 10_000 ||
        fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) {
        return conflict<LocationContextV1>("accuracy_mismatch") to unknown()
    }
    if (!observed.plusSeconds(30).isAfter(now)) return stale<LocationContextV1>(30) to stale(15)
    val movement: ContextSignalV1<MovementContextV1> = when {
        !observed.plusSeconds(15).isAfter(now) -> stale(15)
        fix.speed == null -> unknown()
        fix.speed !in 0.0..150.0 -> conflict("accuracy_mismatch")
        else -> known(MovementContextV1(
            when { fix.speed >= 6 -> "vehicle"; fix.speed >= 0.5 -> "walking"; else -> "stationary" },
            if (accuracy <= 25) 0.8 else 0.5, fix.speed,
            fix.bearing?.takeIf { it.isFinite() && it in 0.0..360.0 }), 15)
    }
    return known(LocationContextV1(fix.latitude, fix.longitude, accuracy, fix.altitude?.takeIf { it.isFinite() }), 30) to movement
}
