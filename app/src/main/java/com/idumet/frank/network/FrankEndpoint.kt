package com.idumet.frank.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object FrankEndpoint {
    fun serverUrl(value: String, releaseBuild: Boolean): HttpUrl {
        val url = value.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Frank server URL is invalid")

        require(url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty()) {
            "Frank server URL must not contain credentials"
        }
        require(url.query == null && url.fragment == null) {
            "Frank server URL must not contain a query or fragment"
        }
        require(!releaseBuild || url.isHttps) {
            "Frank release builds require HTTPS"
        }

        return url
    }

    fun deviceStream(value: String, releaseBuild: Boolean): String {
        val url = serverUrl(value, releaseBuild)
        val httpUrl = url.newBuilder()
            .addPathSegments("v1/device/stream")
            .build()
            .toString()
        return if (url.isHttps) {
            "wss://${httpUrl.removePrefix("https://")}" 
        } else {
            "ws://${httpUrl.removePrefix("http://")}" 
        }
    }

    fun evidenceUpload(
        value: String,
        requestId: String,
        releaseBuild: Boolean
    ): String {
        require(REQUEST_ID.matches(requestId)) { "Frank request ID is invalid" }
        return serverUrl(value, releaseBuild).newBuilder()
            .addPathSegments("v1/device/evidence")
            .addPathSegment(requestId)
            .build()
            .toString()
    }

    private val REQUEST_ID = Regex("[A-Za-z0-9-]{8,96}")
}
