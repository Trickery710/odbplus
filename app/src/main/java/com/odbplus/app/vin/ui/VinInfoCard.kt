package com.odbplus.app.vin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Composable card displaying decoded VIN information.
 *
 * Drop this into any screen that has a [vin] context:
 * ```
 * VinInfoCard(vin = currentVin, modifier = Modifier.fillMaxWidth())
 * ```
 *
 * Renders different states: Loading, Decoded, Partial, Warning, Failed.
 */
@Composable
fun VinInfoCard(
    vin: String,
    modifier: Modifier = Modifier,
    viewModel: VinDecodeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "VinInfoCard"
        ) { state ->
            when (state) {
                is VinDecodeUiState.NotStarted -> VinCardNotStarted()
                is VinDecodeUiState.Loading -> VinCardLoading(state.message)
                is VinDecodeUiState.Ready -> VinCardReady(state) { viewModel.requestRefresh(vin) }
                is VinDecodeUiState.Warning -> VinCardWarning(state) { viewModel.requestRefresh(vin) }
                is VinDecodeUiState.Failed -> VinCardFailed(state) { viewModel.requestRefresh(vin) }
            }
        }
    }
}

@Composable
private fun VinCardNotStarted() {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.DirectionsCar, contentDescription = null,
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(12.dp))
        Text("Vehicle identity not yet decoded", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun VinCardLoading(message: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VinCardReady(
    state: VinDecodeUiState.Ready,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerificationBadge(state.verificationStatus, state.confidence)
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh VIN decode",
                        modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val details = buildList {
            state.engine?.let { add(it) }
            state.bodyClass?.let { add(it) }
            state.fuelType?.let { add(it) }
            state.driveType?.let { add(it) }
        }

        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(4.dp))
        SourceBadge(state.source)

        if (state.warnings.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            state.warnings.take(2).forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun VinCardWarning(
    state: VinDecodeUiState.Warning,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.label.ifBlank { "Verification Warning" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(4.dp))
        state.warnings.take(3).forEach { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VinCardFailed(
    state: VinDecodeUiState.Failed,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("VIN decode failed", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
            Text(state.reason, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun VerificationBadge(status: String, confidence: Float) {
    val (icon, tint) = when (status) {
        "VERIFIED" -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        "MOSTLY_VERIFIED" -> Icons.Default.CheckCircle to Color(0xFF8BC34A)
        "PARTIAL" -> Icons.Default.HourglassEmpty to Color(0xFFFFC107)
        "SUSPECT" -> Icons.Default.Warning to Color(0xFFFF9800)
        else -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    Icon(icon, contentDescription = "Verification: $status", tint = tint,
        modifier = Modifier.size(18.dp))
}

@Composable
private fun SourceBadge(source: String) {
    val label = when (source) {
        "NHTSA" -> "NHTSA vPIC"
        "CACHE" -> "Cached"
        "LOCAL_ONLY" -> "Local only"
        else -> source
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )
}
