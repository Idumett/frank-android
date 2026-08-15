package com.idumet.frank.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryDelayTest {
    @Test
    fun backsOffAndStopsAtTheMaximum() {
        assertEquals(1_000L, RetryDelay.milliseconds(0))
        assertEquals(2_000L, RetryDelay.milliseconds(1))
        assertEquals(8_000L, RetryDelay.milliseconds(3))
        assertEquals(15_000L, RetryDelay.milliseconds(4))
        assertEquals(15_000L, RetryDelay.milliseconds(20))
    }
}
