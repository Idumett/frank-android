package com.idumet.frank.network

data class DeviceTrace(
    val requestId: String,
    val sessionId: String
)

data class DeviceInfo(
    val model: String,
    val fw: String
)

data class DeviceHelloMessage(
    val type: String = "hello",
    val trace: DeviceTrace,
    val device: DeviceInfo
)

data class DeviceStreamMessage(
    val type: String,
    val transcript: String? = null,
    val message: String? = null,
    val bytesBase64: String? = null,
    val reply: DeviceReply? = null
)

data class DeviceReply(
    val speak: String? = null
)

data class DeviceAudioMessage(
    val type: String = "audio",
    val trace: DeviceTrace,
    val mime: String = "audio/wav",
    val audioBase64: String,
    val sentAt: String
)
