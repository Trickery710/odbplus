package com.odbplus.app.expertdiag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.app.ui.theme.AmberContainer
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenContainer
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedContainer
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@Composable
fun AutoTestResultsList(
    results: List<AutoTestResult>,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                text = "AUTOMATIC TESTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp,
            )
            if (running) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = com.odbplus.app.ui.theme.CyanPrimary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Running…", fontSize = 10.sp, color = com.odbplus.app.ui.theme.CyanPrimary)
            }
        }

        if (results.isEmpty() && !running) {
            Text(
                "No automatic test data available.",
                fontSize = 12.sp,
                color = TextTertiary,
            )
            return
        }

        results.forEach { result ->
            AutoTestResultCard(result = result)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AutoTestResultCard(
    result: AutoTestResult,
    modifier: Modifier = Modifier,
) {
    val (bg, border, iconTint) = statusColors(result.status)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusIcon(result.status, iconTint)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.testName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = result.summary,
                fontSize = 11.sp,
                color = iconTint,
                fontWeight = FontWeight.Medium,
            )
            if (result.details.isNotBlank() && result.details != result.summary) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.details,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            if (result.confidenceScore > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Confidence: ${result.confidenceScore}%",
                    fontSize = 10.sp,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: AutoTestStatus, tint: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            AutoTestStatus.PASS -> Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(14.dp))
            AutoTestStatus.FAIL -> Icon(Icons.Filled.Error, null, tint = tint, modifier = Modifier.size(14.dp))
            AutoTestStatus.WARN -> Icon(Icons.Filled.Warning, null, tint = tint, modifier = Modifier.size(14.dp))
            AutoTestStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = tint)
            else -> Icon(Icons.Filled.HelpOutline, null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

private fun statusColors(status: AutoTestStatus): Triple<Color, Color, Color> = when (status) {
    AutoTestStatus.PASS    -> Triple(GreenContainer, GreenSuccess.copy(alpha = 0.4f), GreenSuccess)
    AutoTestStatus.FAIL    -> Triple(RedContainer, RedError.copy(alpha = 0.4f), RedError)
    AutoTestStatus.WARN    -> Triple(AmberContainer, AmberSecondary.copy(alpha = 0.4f), AmberSecondary)
    AutoTestStatus.RUNNING -> Triple(DarkSurfaceVariant, DarkBorder, com.odbplus.app.ui.theme.CyanPrimary)
    else                   -> Triple(DarkSurfaceVariant, DarkBorder, TextTertiary)
}
