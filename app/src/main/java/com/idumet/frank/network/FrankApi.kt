package com.idumet.frank.network

import retrofit2.Response
import retrofit2.http.GET

interface FrankApi {

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}