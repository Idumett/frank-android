package com.idumet.frank

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrankUserMessageTest {
    @Test
    fun connectionErrorsGiveAnActionableMessage() {
        val message = frankUserMessage("WebSocket connection failed at 10.0.2.2")
        assertTrue(message.contains("internet connection"))
        assertFalse(message.contains("10.0.2.2"))
    }

    @Test
    fun unknownProviderDetailsAreNotShown() {
        val message = frankUserMessage("java.lang.IllegalStateException: provider secret")
        assertFalse(message.contains("provider secret"))
        assertTrue(message.contains("try again", ignoreCase = true))
    }
}
