package com.idumet.frank

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun FrankProductScreen() {
    val context = LocalContext.current
    val store = remember { FrankPreferencesStore(context) }
    var preferences by remember { mutableStateOf(store.read()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (preferences.onboardingComplete) {
            FrankSettingsScreen(
                preferences = preferences,
                onPreferencesChanged = { preferences = it },
                store = store,
                onReplayOnboarding = {
                    store.setOnboardingComplete(false)
                    preferences = store.read()
                }
            )
        } else {
            FrankOnboardingScreen(
                onComplete = {
                    store.setOnboardingComplete(true)
                    preferences = store.read()
                }
            )
        }
    }
}

@Composable
private fun FrankOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        Triple(
            "Meet Frank",
            "Frank is a voice-first accessibility assistant for blind and low-vision people.",
            "Ask questions naturally. Frank can use current device context and, when needed, the camera to help describe what is around you."
        ),
        Triple(
            "You stay in control",
            "The microphone is needed to talk to Frank. Camera and location access are optional.",
            "Frank requests visual evidence only when it is useful. You can use voice-only features when camera or location access is off."
        ),
        Triple(
            "Use mobility answers with care",
            "Frank can help you recover when you feel lost, but it is not a navigation or road-crossing service.",
            "Pause somewhere safe and confirm roads, traffic, obstacles and directions with your mobility aids or another trusted source."
        )
    )
    val current = pages[page]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Frank setup, step ${page + 1} of ${pages.size}",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = current.first,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(16.dp))
        Text(current.second, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(current.third, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp),
            onClick = {
                if (page < pages.lastIndex) page++ else onComplete()
            }
        ) {
            Text(if (page < pages.lastIndex) "Continue" else "Finish setup")
        }
        if (page > 0) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                onClick = { page-- }
            ) { Text("Back") }
        }
    }
}

@Composable
private fun FrankSettingsScreen(
    preferences: FrankPreferences,
    onPreferencesChanged: (FrankPreferences) -> Unit,
    store: FrankPreferencesStore,
    onReplayOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var permissionMessage by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionMessage = if (granted) "Permission enabled" else
            "Permission not enabled. You can change this later in Android settings."
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionMessage = when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ->
                "Precise location enabled"
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                "Approximate location enabled"
            else -> "Location remains off. Frank still works without it."
        }
    }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Frank",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Use your configured Android assistant gesture or voice shortcut to start Frank.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        ) { Text("Choose Frank as assistant") }

        SectionHeading("Permissions")
        PermissionButton(
            label = if (granted(Manifest.permission.RECORD_AUDIO)) "Microphone enabled" else "Enable microphone",
            enabled = !granted(Manifest.permission.RECORD_AUDIO),
            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )
        PermissionButton(
            label = if (granted(Manifest.permission.CAMERA)) "Camera enabled" else "Enable optional camera",
            enabled = !granted(Manifest.permission.CAMERA),
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        PermissionButton(
            label = if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) "Location enabled" else "Enable optional location",
            enabled = !(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)),
            onClick = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        )
        if (permissionMessage.isNotEmpty()) Text(permissionMessage)

        SectionHeading("Accessibility")
        PreferenceSwitch(
            title = "State vibration cues",
            detail = "Vibrate differently for listening, thinking, speaking and errors.",
            checked = preferences.hapticsEnabled,
            onCheckedChange = {
                store.setHapticsEnabled(it)
                onPreferencesChanged(store.read())
            }
        )
        PreferenceSwitch(
            title = "Reduce motion",
            detail = "Use a still assistant display and immediate state changes.",
            checked = preferences.reduceMotion,
            onCheckedChange = {
                store.setReduceMotion(it)
                onPreferencesChanged(store.read())
            }
        )

        SectionHeading("Help")
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            onClick = onReplayOnboarding
        ) { Text("Review setup and safety") }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PermissionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
        enabled = enabled,
        onClick = onClick
    ) { Text(label) }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .semantics(mergeDescendants = true) { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
