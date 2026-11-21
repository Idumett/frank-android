package com.idumet.frank.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrankEndpointTest {
    @Test
    fun releaseEndpointUsesSecureWebSocket() {
        assertEquals(
            "wss://frank.example/v1/device/stream",
            FrankEndpoint.deviceStream("https://frank.example/", releaseBuild = true)
        )
    }

    @Test
    fun releaseEndpointRejectsCleartext() {
        assertThrows(IllegalArgumentException::class.java) {
            FrankEndpoint.deviceStream("http://frank.example", releaseBuild = true)
        }
    }

    @Test
    fun endpointRejectsEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            FrankEndpoint.deviceStream("https://token@frank.example", releaseBuild = true)
        }
    }

    @Test
    fun requestIdIsEncodedAsOnePathSegment() {
        assertEquals(
            "https://frank.example/v1/device/evidence/req-12345678",
            FrankEndpoint.evidenceUpload(
                "https://frank.example",
                "req-12345678",
                releaseBuild = true
            )
        )
    }
}
