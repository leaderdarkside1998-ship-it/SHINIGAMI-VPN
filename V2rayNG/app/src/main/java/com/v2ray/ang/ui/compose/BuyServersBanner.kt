package com.v2ray.ang.ui.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

private val MarqueeGap = 56.dp
private const val MarqueeSpeedDpPerSecond = 38f

/**
 * Futuristic announcement ticker that opens a Telegram chat to buy tunneled servers. A dark,
 * theme-derived pill with pointed ends and a thin illuminated accent outline; the message
 * scrolls as a seamless, continuous marquee next to a megaphone icon. The whole banner is one
 * tappable node. There are no pagination dots.
 */
@Composable
fun BuyServersBanner(
    text: String,
    telegramUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val shape = CutCornerShape(percent = 50)
    val base = colors.surfaceContainerHigh
    val background = Brush.horizontalGradient(listOf(base, lerp(base, primary, 0.14f), base))

    // The ticker scrolls right-to-left and the ornaments are fixed to physical sides, so the
    // banner is laid out left-to-right regardless of the app language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(44.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = primary.copy(alpha = 0.25f),
                    spotColor = primary.copy(alpha = 0.45f)
                )
                .clip(shape)
                .background(background)
                .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = shape)
                .clickable(onClickLabel = text, role = Role.Button) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
                    } catch (_: ActivityNotFoundException) {
                        // No app/browser can handle it; silently ignore.
                    }
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevrons_forward_24dp),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_campaign_24dp),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            MarqueeText(
                text = text,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevrons_forward_24dp),
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .size(18.dp)
                    .scale(scaleX = -1f, scaleY = 1f)
            )
        }
    }
}

/**
 * Single-line text that scrolls continuously and seamlessly: the text is repeated with a gap
 * and the strip is translated by exactly one period per cycle, so the loop point is
 * indistinguishable from the rest of the motion. Duplicates are hidden from accessibility.
 */
@Composable
private fun MarqueeText(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var viewportPx by remember { mutableIntStateOf(0) }
    var periodPx by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }

    LaunchedEffect(periodPx) {
        if (periodPx > 0) {
            val periodDp = periodPx / density.density
            val durationMs = (periodDp / MarqueeSpeedDpPerSecond * 1000f).toInt().coerceAtLeast(1)
            offset.snapTo(0f)
            offset.animateTo(
                targetValue = periodPx.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    val copies = if (periodPx > 0 && viewportPx > 0) viewportPx / periodPx + 2 else 1

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewportPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -offset.value }
        ) {
            repeat(copies) { index ->
                MarqueeItem(
                    text = text,
                    gap = MarqueeGap,
                    modifier = if (index == 0) {
                        Modifier.onSizeChanged { periodPx = it.width }
                    } else {
                        Modifier.clearAndSetSemantics { }
                    }
                )
            }
        }
    }
}

@Composable
private fun MarqueeItem(text: String, gap: Dp, modifier: Modifier = Modifier) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(end = gap)
    )
}
