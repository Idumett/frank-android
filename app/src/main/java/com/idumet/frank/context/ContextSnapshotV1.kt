package com.idumet.frank.context

data class ContextProvenanceV1(val source: String, val collectedAt: String)
data class ContextSignalV1<T>(
    val status: String, val value: T? = null, val observedAt: String? = null,
    val expiresAt: String? = null, val reason: String? = null, val permission: String? = null,
    val lastObservedAt: String? = null, val expiredAt: String? = null,
    val sources: List<String>? = null, val provenance: ContextProvenanceV1
)
data class TimeContextV1(val timezone: String, val utcOffsetMinutes: Int)
data class PermissionContextV1(
    val microphone: ContextSignalV1<String>, val camera: ContextSignalV1<String>,
    val location: ContextSignalV1<String>, val activity: ContextSignalV1<String>
)
data class DeviceContextV1(
    val connectivity: ContextSignalV1<String>, val screen: ContextSignalV1<String>,
    val headphones: ContextSignalV1<Boolean>, val batteryPercent: ContextSignalV1<Int>,
    val cameraAvailable: ContextSignalV1<Boolean>
)
data class AppContextV1(val state: String, val sessionOpen: Boolean)
data class LocationContextV1(
    val latitude: Double, val longitude: Double, val accuracyMeters: Double,
    val altitudeMeters: Double? = null
)
data class MovementContextV1(
    val state: String, val confidence: Double, val speedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null
)
data class EvidenceContextV1(
    val frameId: String, val capturedAt: String, val ageMs: Long, val quality: String
)
data class ContextSnapshotV1(
    val schemaVersion: String = "1", val snapshotId: String, val sessionId: String,
    val capturedAt: String, val expiresAt: String, val time: ContextSignalV1<TimeContextV1>,
    val permissions: PermissionContextV1, val device: DeviceContextV1,
    val app: ContextSignalV1<AppContextV1>, val location: ContextSignalV1<LocationContextV1>,
    val movement: ContextSignalV1<MovementContextV1>, val evidence: ContextSignalV1<EvidenceContextV1>
)

object ContextSnapshotValidatorV1 {
    fun validate(snapshot: ContextSnapshotV1, nowMillis: Long = System.currentTimeMillis()) {
        require(snapshot.schemaVersion == "1") { "Unsupported context snapshot version" }
        require(snapshot.snapshotId.length >= 8 && snapshot.sessionId.isNotBlank()) { "Context identity is invalid" }
        val captured = java.time.Instant.parse(snapshot.capturedAt).toEpochMilli()
        val expires = java.time.Instant.parse(snapshot.expiresAt).toEpochMilli()
        require(captured <= nowMillis + 10_000 && expires > nowMillis && expires > captured && expires - captured <= 60_000) { "Context timing is invalid" }
        listOf(
            snapshot.time, snapshot.permissions.microphone, snapshot.permissions.camera,
            snapshot.permissions.location, snapshot.permissions.activity,
            snapshot.device.connectivity, snapshot.device.screen, snapshot.device.headphones,
            snapshot.device.batteryPercent, snapshot.device.cameraAvailable, snapshot.app,
            snapshot.location, snapshot.movement, snapshot.evidence
        ).forEach { validateSignal(it, nowMillis) }
    }

    private fun validateSignal(signal: ContextSignalV1<*>, nowMillis: Long) {
        require(signal.status in setOf("known", "unknown", "denied", "stale", "conflicting")) { "Unknown context status" }
        require(signal.provenance.source in setOf("android_system", "android_sensor", "android_app")) { "Unknown context provenance" }
        val collected = java.time.Instant.parse(signal.provenance.collectedAt).toEpochMilli()
        require(collected <= nowMillis + 10_000) { "Context provenance is in the future" }
        when (signal.status) {
            "known" -> {
                require(signal.value != null && signal.observedAt != null && signal.expiresAt != null) { "Known context is incomplete" }
                val observed = java.time.Instant.parse(signal.observedAt).toEpochMilli()
                val expires = java.time.Instant.parse(signal.expiresAt).toEpochMilli()
                require(observed <= nowMillis + 10_000 && expires > observed) { "Known context timing is invalid" }
            }
            "unknown" -> require(signal.reason in setOf("unavailable", "not_collected", "not_supported")) { "Unknown context needs a valid reason" }
            "denied" -> require(signal.permission in setOf("microphone", "camera", "location", "activity")) { "Denied context needs a valid permission" }
            "stale" -> {
                require(signal.lastObservedAt != null && signal.expiredAt != null) { "Stale context is incomplete" }
                require(!java.time.Instant.parse(signal.expiredAt).isBefore(java.time.Instant.parse(signal.lastObservedAt))) { "Stale context timing is invalid" }
            }
            "conflicting" -> {
                require((signal.sources?.size ?: 0) in 2..4) { "Conflicting context needs two to four sources" }
                require(signal.reason in setOf("sensor_disagreement", "invalid_sequence", "accuracy_mismatch")) { "Conflicting context needs a valid reason" }
            }
        }
    }
}
