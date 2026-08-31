package com.idumet.frank.context

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.idumet.frank.camera.CameraFrame
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FrankContextEngine(context: Context) {
    private val app = context.applicationContext
    private val locationClient = LocationServices.getFusedLocationProviderClient(app)
    @Volatile private var appState = "idle"
    @Volatile private var sessionOpen = true
    @Volatile private var evidence: CameraFrame? = null
    @Volatile private var lastMovement: ContextSignalV1<MovementContextV1>? = null

    fun updateAppState(value: String) { appState = value }
    fun updateSessionOpen(value: Boolean) {
        sessionOpen = value
        if (!value) {
            evidence = null
            lastMovement = null
        }
    }
    fun updateEvidence(frame: CameraFrame) { evidence = frame }

    suspend fun snapshot(sessionId: String): ContextSnapshotV1 {
        val now = Instant.now(); val expires = now.plusSeconds(30); val system = provenance("android_system", now)
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationPermission = when { precise -> known("granted", now, expires, system); coarse -> known("approximate", now, expires, system); else -> denied<String>("location", system) }
        val location = if (precise || coarse) lastLocation(now, expires) else denied<LocationContextV1>("location", system)
        val movement = when (location.status) {
            "known" -> lastMovement ?: unknown("not_collected", provenance("android_sensor", now))
            "denied" -> denied<MovementContextV1>("location", provenance("android_system", now))
            "stale" -> stale(
                Instant.parse(location.lastObservedAt!!),
                Instant.parse(location.expiredAt!!),
                provenance("android_sensor", now)
            )
            else -> unknown("not_collected", provenance("android_sensor", now))
        }
        val frame = evidence
        val evidenceSignal = if (frame == null) unknown<EvidenceContextV1>("not_collected", provenance("android_app", now)) else {
            val captured = Instant.ofEpochMilli(frame.capturedAtMs)
            if (captured.plusSeconds(4).isBefore(now)) stale(captured, captured.plusSeconds(4), provenance("android_app", now))
            else known(EvidenceContextV1(frame.frameId, captured.toString(), now.toEpochMilli() - frame.capturedAtMs, frame.quality), captured, captured.plusSeconds(4), provenance("android_app", now))
        }
        return ContextSnapshotV1(
            snapshotId = UUID.randomUUID().toString(), sessionId = sessionId,
            capturedAt = now.toString(), expiresAt = expires.toString(),
            time = known(time(), now, expires, system),
            permissions = PermissionContextV1(
                permission(Manifest.permission.RECORD_AUDIO, "microphone", now, expires),
                permission(Manifest.permission.CAMERA, "camera", now, expires), locationPermission,
                permission(Manifest.permission.ACTIVITY_RECOGNITION, "activity", now, expires)
            ),
            device = DeviceContextV1(
                known(connectivity(), now, expires, system), known(screen(), now, expires, system),
                known(headphones(), now, expires, system), battery(now, expires, system),
                known(app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY), now, expires, system)
            ),
            app = known(AppContextV1(appState, sessionOpen), now, expires, provenance("android_app", now)),
            location = location, movement = movement, evidence = evidenceSignal
        )
    }

    @Suppress("MissingPermission")
    private suspend fun lastLocation(now: Instant, expires: Instant): ContextSignalV1<LocationContextV1> = suspendCoroutine { continuation ->
        try {
            locationClient.lastLocation.addOnCompleteListener { task ->
                val value = task.takeIf { it.isSuccessful }?.result
                if (value == null) continuation.resume(unknown("unavailable", provenance("android_sensor", now)))
                else {
                    val observed = Instant.ofEpochMilli(value.time)
                    if (observed.isAfter(now.plusSeconds(10))) {
                        continuation.resume(conflicting("invalid_sequence", listOf("gps_time", "system_time"), provenance("android_sensor", now)))
                        return@addOnCompleteListener
                    }
                    if (!value.accuracy.isFinite() || value.accuracy <= 0f || value.accuracy > 10_000f) {
                        continuation.resume(conflicting("accuracy_mismatch", listOf("gps", "location_accuracy"), provenance("android_sensor", now)))
                        return@addOnCompleteListener
                    }
                    lastMovement = if (value.hasSpeed()) {
                        val speed = value.speed.toDouble()
                        if (speed !in 0.0..150.0) conflicting("accuracy_mismatch", listOf("gps", "speed"), provenance("android_sensor", now))
                        else {
                            val state = when { speed >= 6.0 -> "vehicle"; speed >= 0.5 -> "walking"; else -> "stationary" }
                            known(MovementContextV1(state, if (value.accuracy <= 25f) 0.8 else 0.5, speed, value.bearing.toDouble().takeIf { value.hasBearing() }), observed, minOf(expires, observed.plusSeconds(15)), provenance("android_sensor", now))
                        }
                    } else unknown("unavailable", provenance("android_sensor", now))
                    if (observed.plusSeconds(30).isBefore(now)) continuation.resume(stale(observed, observed.plusSeconds(30), provenance("android_sensor", now)))
                    else continuation.resume(known(LocationContextV1(value.latitude, value.longitude, value.accuracy.toDouble(), value.altitude.takeIf { value.hasAltitude() }), observed, minOf(expires, observed.plusSeconds(30)), provenance("android_sensor", now)))
                }
            }
        } catch (_: SecurityException) {
            continuation.resume(denied("location", provenance("android_system", now)))
        }
    }

    private fun time(): TimeContextV1 { val now = ZonedDateTime.now(); return TimeContextV1(ZoneId.systemDefault().id, now.offset.totalSeconds / 60) }
    private fun connectivity(): String { val manager = app.getSystemService(ConnectivityManager::class.java); val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"; return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "online" else "limited" }
    private fun screen(): String { val power = app.getSystemService(PowerManager::class.java); val keyguard = app.getSystemService(KeyguardManager::class.java); return when { !power.isInteractive -> "off"; keyguard.isKeyguardLocked -> "locked"; else -> "interactive" } }
    private fun headphones(): Boolean = app.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET) }
    private fun battery(now: Instant, expires: Instant, source: ContextProvenanceV1): ContextSignalV1<Int> { val value = app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); return if (value in 0..100) known(value, now, expires, source) else unknown("unavailable", source) }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    private fun permission(permission: String, name: String, now: Instant, expires: Instant) = if (granted(permission)) known("granted", now, expires, provenance("android_system", now)) else denied<String>(name, provenance("android_system", now))
    private fun provenance(source: String, now: Instant) = ContextProvenanceV1(source, now.toString())
    private fun <T> known(value: T, observed: Instant, expires: Instant, provenance: ContextProvenanceV1) = ContextSignalV1(status = "known", value = value, observedAt = observed.toString(), expiresAt = expires.toString(), provenance = provenance)
    private fun <T> unknown(reason: String, provenance: ContextProvenanceV1) = ContextSignalV1<T>(status = "unknown", reason = reason, provenance = provenance)
    private fun <T> denied(permission: String, provenance: ContextProvenanceV1) = ContextSignalV1<T>(status = "denied", permission = permission, provenance = provenance)
    private fun <T> stale(observed: Instant, expired: Instant, provenance: ContextProvenanceV1) = ContextSignalV1<T>(status = "stale", lastObservedAt = observed.toString(), expiredAt = expired.toString(), provenance = provenance)
    private fun <T> conflicting(reason: String, sources: List<String>, provenance: ContextProvenanceV1) = ContextSignalV1<T>(status = "conflicting", reason = reason, sources = sources, provenance = provenance)
}
