package com.idumet.frank

import android.content.Context
import android.provider.Settings

data class FrankPreferences(
    val onboardingComplete: Boolean,
    val hapticsEnabled: Boolean,
    val reduceMotion: Boolean
)

class FrankPreferencesStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "frank_preferences_v1",
        Context.MODE_PRIVATE
    )

    fun read(): FrankPreferences = FrankPreferences(
        onboardingComplete = preferences.getBoolean("onboarding_complete", false),
        hapticsEnabled = preferences.getBoolean("haptics_enabled", true),
        reduceMotion = preferences.getBoolean(
            "reduce_motion",
            systemRequestsReducedMotion(appContext)
        )
    )

    fun setOnboardingComplete(complete: Boolean) {
        preferences.edit().putBoolean("onboarding_complete", complete).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun setReduceMotion(enabled: Boolean) {
        preferences.edit().putBoolean("reduce_motion", enabled).apply()
    }
}

fun systemRequestsReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)
