package com.uchet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uchet.ui.PipeTrackerApp
import com.uchet.ui.theme.PipeTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipeTrackerTheme {
                PipeTrackerApp()
            }
        }
    }
}
