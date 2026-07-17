package com.idumet.frank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idumet.frank.ui.theme.FrankTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FrankTheme {
                FrankDebugScreen()
            }
        }
    }
}