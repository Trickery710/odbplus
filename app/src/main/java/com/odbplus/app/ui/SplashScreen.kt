package com.odbplus.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odbplus.app.ui.theme.DarkBackground
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(600))
        delay(1800)
        alpha.animateTo(0f, animationSpec = tween(400))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo area: bolt icon + ODB+ wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hex bolt icon
                Canvas(modifier = Modifier.size(56.dp)) {
                    drawHexBoltIcon()
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ODB+ wordmark
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "ODB",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = Color.White,
                        lineHeight = 52.sp
                    )
                    Text(
                        text = "+",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00),
                        lineHeight = 52.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Divider line
            Canvas(modifier = Modifier.size(width = 200.dp, height = 1.dp)) {
                drawLine(
                    color = Color(0xFFFF6B00).copy(alpha = 0.6f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sublabel
            Text(
                text = "DIAGNOSTIC SYSTEMS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawHexBoltIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = size.minDimension / 2f - 2.dp.toPx()

    // Hex polygon points
    val hexPoints = (0 until 6).map { i ->
        val angle = Math.toRadians((60.0 * i) - 30.0)
        Offset(
            cx + outerR * cos(angle).toFloat(),
            cy + outerR * sin(angle).toFloat()
        )
    }
    val hexPath = Path().apply {
        moveTo(hexPoints[0].x, hexPoints[0].y)
        for (i in 1 until 6) lineTo(hexPoints[i].x, hexPoints[i].y)
        close()
    }

    // Draw hex fill with orange gradient
    drawPath(
        path = hexPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF8C1A), Color(0xFFCC5500)),
            startY = cy - outerR,
            endY = cy + outerR
        )
    )

    // Draw hex stroke
    drawPath(
        path = hexPath,
        color = Color(0xFFCC5500),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Inner dark circle
    val innerR = outerR * 0.38f
    drawCircle(color = Color(0xFF1C1C1C), radius = innerR, center = Offset(cx, cy))
    drawCircle(
        color = Color(0xFFFF6B00),
        radius = innerR,
        center = Offset(cx, cy),
        style = Stroke(width = 1.dp.toPx())
    )

    // Circuit line right
    val lineEnd = Offset(cx + outerR + 6.dp.toPx(), cy)
    drawLine(
        color = Color(0xFFFF6B00),
        start = Offset(cx + innerR, cy),
        end = lineEnd,
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
    drawCircle(color = Color(0xFFFF6B00), radius = 2.dp.toPx(), center = lineEnd)

    // Circuit line down
    val lineDown = Offset(cx, cy + outerR + 6.dp.toPx())
    drawLine(
        color = Color(0xFFFF6B00),
        start = Offset(cx, cy + innerR),
        end = lineDown,
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
    drawCircle(color = Color(0xFFFF6B00), radius = 2.dp.toPx(), center = lineDown)

    // Small gold wrench diagonal marks
    val goldColor = Color(0xFFFFD700).copy(alpha = 0.6f)
    drawLine(
        color = goldColor,
        start = Offset(cx - outerR * 0.55f, cy - outerR * 0.55f),
        end = Offset(cx - outerR * 0.2f, cy - outerR * 0.2f),
        strokeWidth = 2.5.dp.toPx()
    )
    drawLine(
        color = goldColor,
        start = Offset(cx + outerR * 0.2f, cy + outerR * 0.2f),
        end = Offset(cx + outerR * 0.55f, cy + outerR * 0.55f),
        strokeWidth = 2.5.dp.toPx()
    )
}
