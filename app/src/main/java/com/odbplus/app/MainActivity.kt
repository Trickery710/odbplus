package com.odbplus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.odbplus.app.nav.AppNavHost
import com.odbplus.app.nav.BottomBar
import com.odbplus.app.ui.ProvideTransport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OdbPlusApp() }
    }
}

@Composable
fun OdbPlusApp() {
    MaterialTheme {
        ProvideTransport {
            val nav = rememberNavController()
            Scaffold(
                bottomBar = { BottomBar(nav) }
            ) { inner: PaddingValues ->
                AppNavHost(nav = nav, modifier = Modifier.padding(inner))
            }
        }
    }
}
