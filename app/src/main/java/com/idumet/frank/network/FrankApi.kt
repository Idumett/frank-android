package com.idumet.frank.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FrankApi {

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("v1/stt")
    suspend fun transcribe(
        @Body request: SttRequest
    ): Response<SttResponse>
}