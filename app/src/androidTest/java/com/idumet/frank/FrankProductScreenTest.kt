package com.idumet.frank

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.idumet.frank.ui.theme.FrankTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FrankProductScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetOnboarding() {
        composeRule.activity.getSharedPreferences(
            "frank_preferences_v1",
            ComponentActivity.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun onboardingHasAReadableOrderedFlow() {
        composeRule.setContent { FrankTheme { FrankProductScreen() } }

        composeRule.onNodeWithText("Meet Frank").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        composeRule.onNodeWithText("You stay in control").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Use mobility answers with care").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").performClick()
        composeRule.onNodeWithText("Accessibility").assertIsDisplayed()
    }

    @Test
    fun hapticPreferenceCanBeChangedWithoutVision() {
        FrankPreferencesStore(composeRule.activity).setOnboardingComplete(true)
        composeRule.setContent { FrankTheme { FrankProductScreen() } }

        composeRule.onNodeWithText("State vibration cues").performClick()
        composeRule.onNodeWithText("State vibration cues").assertIsDisplayed()
    }
}
