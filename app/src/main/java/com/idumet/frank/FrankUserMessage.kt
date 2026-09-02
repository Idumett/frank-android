package com.idumet.frank

fun frankUserMessage(message: String?): String {
    val value = message.orEmpty().lowercase()
    return when {
        value.contains("401") || value.contains("unauthor") ->
            "Frank could not verify this app. Check the app configuration and try again."
        value.contains("timeout") || value.contains("timed out") ->
            "Frank took too long to respond. Check your connection and try again."
        value.contains("connect") || value.contains("network") || value.contains("socket") ->
            "Frank cannot reach the server. Check your internet connection; Frank will keep trying."
        value.contains("microphone") || value.contains("record") || value.contains("audio") ->
            "Frank could not use the microphone. Check microphone permission and try again."
        value.contains("camera") || value.contains("image") || value.contains("frame") ->
            "Frank could not use the camera. You can continue with voice, or check camera permission."
        value.isBlank() -> "Something interrupted the request. Please try again."
        else -> "Something interrupted the request. Please try again."
    }
}
