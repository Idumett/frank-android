package com.idumet.frank.network

data class TtsConfig(
    val languageCode: String? = null,
    val voiceName: String? = null,
    val speakingRate: Double? = null,
    val pitch: Double? = null
)

data class Trace(
    val requestId: String,
    val sessionId: String? = null,
    val tts: TtsConfig? = null
)

data class SttRequest(
    val trace: Trace,
    val audioBase64: String
)

data class SttResponse(
    val trace: Trace,
    val transcript: String,
    val confidence: Double?
)