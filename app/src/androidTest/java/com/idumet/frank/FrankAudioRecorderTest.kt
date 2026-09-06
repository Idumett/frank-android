package com.idumet.frank

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import com.idumet.frank.service.AudioRecorder
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FrankAudioRecorderTest {
    @Test fun microphoneProducesValidWavAndCanRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val recorder = AudioRecorder(context)
        try {
            repeat(2) {
                val samples = CountDownLatch(2)
                recorder.setAudioSampleListener { _, count -> if (count > 0) samples.countDown() }
                recorder.start()
                assertTrue("Microphone delivered no samples", samples.await(5, TimeUnit.SECONDS))
                val file = recorder.stop()
                assertNotNull(file)
                try {
                    val bytes = file!!.readBytes()
                    val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
                    assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
                    assertEquals(bytes.size - 8, header.getInt(4))
                    assertEquals(1, header.getShort(20).toInt()) // PCM
                    assertEquals(1, header.getShort(22).toInt()) // mono
                    assertEquals(16_000, header.getInt(24))
                    assertEquals(16, header.getShort(34).toInt())
                    assertEquals(bytes.size - 44, header.getInt(40))
                    assertTrue(bytes.size > 44)
                } finally {
                    file?.delete()
                }
            }
        } finally {
            recorder.cancel()
        }
    }
}
