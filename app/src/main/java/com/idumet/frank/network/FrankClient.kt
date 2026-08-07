package com.idumet.frank.network

import com.idumet.frank.BuildConfig

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object FrankClient {

    private val baseUrl = BuildConfig.FRANK_SERVER_URL.let {
        if (it.endsWith("/")) it else "$it/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
//        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                if (BuildConfig.FRANK_DEMO_TOKEN.isNotBlank()) {
                    header("Authorization", "Bearer ${BuildConfig.FRANK_DEMO_TOKEN}")
                }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()

    val api: FrankApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FrankApi::class.java)
    }
}
