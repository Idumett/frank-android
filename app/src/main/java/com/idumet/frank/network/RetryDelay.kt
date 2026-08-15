package com.idumet.frank.network

object RetryDelay {
    fun milliseconds(
        attempt: Int,
        maximumMs: Long = 15_000L
    ): Long {
        val safeAttempt = attempt.coerceIn(0, 10)
        return (1_000L shl safeAttempt)
            .coerceAtMost(maximumMs)
    }
}
