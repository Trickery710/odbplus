package com.odbplus.app.expertdiag.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odbplus.app.expertdiag.model.RootCauseProbability
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary

@Composable
fun RootCauseProbabilitySection(
    probabilities: List<RootCauseProbability>,
    modifier: Modifier = Modifier,
) {
    if (probabilities.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "ROOT CAUSE PROBABILITY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        probabilities.take(5).forEach { cause ->
            RootCauseProbabilityBar(cause = cause)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun RootCauseProbabilityBar(
    cause: RootCauseProbability,
    modifier: Modifier = Modifier,
) {
    var started by remember { mutableStateOf(false) }
    val animatedFraction by animateFloatAsState(
        targetValue = if (started) cause.probability else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "prob_bar",
    )
    LaunchedEffect(cause.probability) { started = true }

    val barColor = probabilityColor(cause.probability)
    val percent = (cause.probability * 100).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cause.cause,
                fontSize = 12.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }

        if (cause.supportingEvidence.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            cause.supportingEvidence.take(2).forEach { ev ->
                Text(
                    text = "• $ev",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

private fun probabilityColor(p: Float): Color = when {
    p > 0.6f -> RedError
    p > 0.35f -> AmberSecondary
    else -> GreenSuccess
}
