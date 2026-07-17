package com.idumet.frank.network

import android.util.Base64
import java.io.File
import java.util.UUID

object SttClient {

    suspend fun transcribe(file: File): SttResponse {

        val bytes = file.readBytes()

        val base64 = Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )

        val request = SttRequest(
            trace = Trace(
                requestId = UUID.randomUUID().toString()
            ),
            audioBase64 = base64
        )

        val response =
            FrankClient.api.transcribe(request)

        if (!response.isSuccessful)
            throw Exception("HTTP ${response.code()}")

//        return response.body()
//            ?: throw Exception("Empty response")
        val body = response.body()

        if (body == null) {
            throw Exception("Response body was null")
        }

        return body
    }
}