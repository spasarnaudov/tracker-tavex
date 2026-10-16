package io.github.spasarnaudov.trackertavex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.spasarnaudov.trackertavex.navigation.AppNavHost
import io.github.spasarnaudov.trackertavex.ui.theme.TrackerTavexAndroidTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackerTavexAndroidTheme {
                AppNavHost()
            }
        }
    }
}
