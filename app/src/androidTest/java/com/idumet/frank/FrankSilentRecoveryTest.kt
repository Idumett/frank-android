package com.idumet.frank

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList

/** Opt in against a configured test server with -e runLiveRecovery true. */
class FrankSilentRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun silentListeningReturnsToReadyAndCanBeRestarted() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveRecovery") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = compose.activity.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)
        fun microphoneMode(mode: String) {
            instrumentation.uiAutomation.executeShellCommand("appops set $packageName RECORD_AUDIO $mode").use {
                FileInputStream(it.fileDescriptor).readBytes()
            }
        }
        // Android delivers silence without recording the tester's real microphone.
        microphoneMode("ignore")
        val statuses = CopyOnWriteArrayList<String>()
        val states = CopyOnWriteArrayList<AssistantState>()
        try {
            compose.setContent {
                FrankConversationHost(
                    autoConnect = true, autoStartRecording = true,
                    showConnectButton = false, showServerHealthControls = false,
                    showCloseButton = false,
                    onAssistantStateChanged = { states.add(it) },
                    onStatusMessageChanged = { statuses.add(it) }
                )
            }
            compose.waitUntil(25_000) { statuses.any { it.startsWith("I didn’t hear anything") } }
            compose.waitForIdle()
            compose.onNodeWithText("Speak to Frank").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { statuses.count { it.startsWith("I didn’t hear anything") } == 2 }
            compose.onNodeWithText("Speak to Frank").assertIsEnabled()
            assertFalse("Silence started a server request: $states", states.contains(AssistantState.Thinking))
            assertFalse("Silence caused an error: $statuses", states.contains(AssistantState.Error))
            assertEquals(2, states.count { it == AssistantState.Listening })
        } finally {
            microphoneMode("allow")
        }
    }
}
