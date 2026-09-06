package com.idumet.frank

import android.Manifest
import android.location.Location
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.idumet.frank.camera.FrankCamera
import com.idumet.frank.context.ContextSnapshotValidatorV1
import com.idumet.frank.context.FrankContextEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FrankSensorsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun cameraProducesFreshEvidenceAfterRestart() {
        instrumentation.uiAutomation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        var ready = CountDownLatch(1)
        val errors = mutableListOf<String>()
        val camera = FrankCamera(compose.activity, onFrameCaptured = { ready.countDown() }, onError = { errors.add(it) })
        try {
            compose.runOnUiThread { camera.start(compose.activity) }
            assertTrue("No camera frame: $errors", ready.await(15, TimeUnit.SECONDS))
            val first = camera.copyFreshFrame(4_000)
            assertNotNull(first)
            assertTrue(first!!.file.length() > 0)
            first.file.delete()
            compose.runOnUiThread { camera.stop() }
            instrumentation.waitForIdleSync()
            ready = CountDownLatch(1)
            compose.runOnUiThread { camera.start(compose.activity) }
            assertTrue("No frame after restart: $errors", ready.await(15, TimeUnit.SECONDS))
            val second = camera.copyFreshFrame(4_000)
            assertNotNull(second)
            assertNotEquals(first.frameId, second!!.frameId)
            second.file.delete()
        } finally {
            compose.runOnUiThread { camera.release() }
        }
    }

    @Test fun currentLocationReachesAValidSnapshot() {
        val app = compose.activity
        for (permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, permission)
        }
        instrumentation.uiAutomation.executeShellCommand("appops set ${app.packageName} android:mock_location allow").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        val client = LocationServices.getFusedLocationProviderClient(app)
        try {
            Tasks.await(client.setMockMode(true), 5, TimeUnit.SECONDS)
            val fix = Location("fused").apply {
                latitude = 51.5; longitude = -0.1; accuracy = 15f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            Tasks.await(client.setMockLocation(fix), 5, TimeUnit.SECONDS)
            val snapshot = runBlocking { FrankContextEngine(app).snapshot("sensor-test-session") }
            ContextSnapshotValidatorV1.validate(snapshot)
            assertEquals("known", snapshot.location.status)
            assertEquals(51.5, snapshot.location.value!!.latitude, 0.001)
            assertEquals("granted", snapshot.permissions.location.value)
        } finally {
            Tasks.await(client.setMockMode(false), 5, TimeUnit.SECONDS)
            instrumentation.uiAutomation.executeShellCommand("appops set ${app.packageName} android:mock_location default").close()
        }
    }
}
