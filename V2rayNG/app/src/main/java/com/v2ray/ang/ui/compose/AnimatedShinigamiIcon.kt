package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small hooded-reaper ("shinigami") glyph that continuously slides from the top of its
 * bounds to the bottom and loops, tinted with [tint] (normally the current accent color) so it
 * stands out against whichever theme the user picked. Shown in the top bar while a ping test
 * is running.
 */
@Composable
fun AnimatedShinigamiIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 22.dp
) {
    val transition = rememberInfiniteTransition(label = "shinigami_fall")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shinigami_fall_progress"
    )

    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        // Slides from just above the top edge to just below the bottom edge, then loops.
        val travel = h * 1.6f
        val yOffset = -h * 0.3f + progress * travel

        translate(0f, yOffset) {
            val hoodPath = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 0.15f, h * 0.05f, w * 0.05f, h * 0.55f, w * 0.12f, h * 0.62f)
                lineTo(w * 0.30f, h * 0.50f)
                lineTo(w * 0.5f, h * 0.62f)
                lineTo(w * 0.70f, h * 0.50f)
                lineTo(w * 0.88f, h * 0.62f)
                cubicTo(w * 0.95f, h * 0.55f, w * 0.85f, h * 0.05f, w * 0.5f, 0f)
                close()
            }
            drawPath(hoodPath, color = tint)

            // Void where the face would be
            drawOval(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(w * 0.34f, h * 0.18f),
                size = Size(w * 0.32f, h * 0.30f)
            )

            // Robe body trailing below the hood
            val robePath = Path().apply {
                moveTo(w * 0.22f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.68f)
                lineTo(w * 0.78f, h * 0.55f)
                lineTo(w * 0.92f, h)
                lineTo(w * 0.08f, h)
                close()
            }
            drawPath(robePath, color = tint.copy(alpha = 0.9f))

            // Tiny scythe accent
            drawLine(
                color = tint,
                start = Offset(w * 0.86f, h * 0.05f),
                end = Offset(w * 0.62f, h * 0.5f),
                strokeWidth = w * 0.045f,
                cap = StrokeCap.Round
            )
        }
    }
}
