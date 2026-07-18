package com.idumet.frank

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idumet.frank.permissions.PermissionManager

@Composable
fun FrankPermissionSection(
    onPermissionsGranted: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val microphoneGranted =
                permissions["android.permission.RECORD_AUDIO"] == true

            val cameraGranted =
                permissions["android.permission.CAMERA"] == true

            val allGranted =
                microphoneGranted && cameraGranted

            Toast.makeText(
                context,
                if (allGranted) {
                    "Frank is ready"
                } else {
                    "Microphone and camera permissions are required"
                },
                Toast.LENGTH_LONG
            ).show()

            if (allGranted) {
                onPermissionsGranted?.invoke()
            }
        }

    Text(
        text = "Frank needs microphone and camera access.",
        modifier = Modifier.padding(top = 16.dp)
    )

    Button(
        modifier = Modifier.padding(top = 16.dp),
        onClick = {
            permissionLauncher.launch(
                PermissionManager.requiredPermissions()
            )
        }
    ) {
        Text("Enable Frank")
    }
}