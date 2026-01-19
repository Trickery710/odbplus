package com.odbplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// LiveScreen is in LiveScreen.kt
// CodesScreen is in DiagnosticsScreen.kt
@Composable fun DiagnosticsScreen() { CenteredStub("Diagnostics (placeholder)") }
@Composable fun EcuProfileScreen() { CenteredStub("ECU Profile (placeholder)") }

@Composable
private fun CenteredStub(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title)
    }
}
