package com.obdplus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.obdplus.app.nav.AppScreen
import com.obdplus.app.ui.theme.Obdplus_multi_module_scaffoldTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.view.WindowCompat
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Apply your app's theme
            Obdplus_multi_module_scaffoldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // This is the entry point for your UI
                    AppScreen()
                }
            }
        }
    }
}
