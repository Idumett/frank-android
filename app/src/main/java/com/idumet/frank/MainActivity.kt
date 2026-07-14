package com.idumet.frank

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idumet.frank.permissions.PermissionManager
import com.idumet.frank.ui.theme.FrankTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FrankTheme {
                PermissionScreen()
            }
        }
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val allGranted = permissions.values.all { granted ->
            granted
        }

        Toast.makeText(
            context,
            if (allGranted) {
                "Frank is ready"
            } else {
                "Microphone and camera permissions are required"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Frank needs microphone and camera access.")

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
}