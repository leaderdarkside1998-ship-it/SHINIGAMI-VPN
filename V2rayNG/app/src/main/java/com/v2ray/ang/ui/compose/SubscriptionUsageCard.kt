package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlin.math.roundToLong

/**
 * Subscription traffic quota for the current group (from the `subscription-userinfo` header
 * captured on the last update). One full-width horizontal usage bar with a separate round
 * refresh button to its right, a clear gap in between. The consumed amount is shown inside the
 * filled (bright) part and the remaining amount inside the unfilled (dark) part; there is no
 * second used/remaining summary anywhere else. When the subscription never reported quota info
 * the same bar shows the quiet "no data yet" hint, and when no total limit is known (unlimited)
 * only the consumed amount is shown.
 *
 * The card is laid out left-to-right in every language so the bar fills from the left and the
 * refresh button always sits on the right; the text itself still follows its own script.
 */
@Composable
fun SubscriptionUsageCard(
    usage: SubscriptionItem?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val used = (usage?.trafficUploadBytes ?: 0L) + (usage?.trafficDownloadBytes ?: 0L)
    val total = usage?.trafficTotalBytes
    val hasQuota = usage != null && (usage.trafficUploadBytes != null || usage.trafficDownloadBytes != null || total != null)
    val hasLimit = hasQuota && total != null && total > 0
    val fraction = usageFraction(used, total)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 100f),
        label = "subscriptionUsageFraction"
    )

    val usedText = if (hasQuota) stringResource(R.string.subscription_usage_used_inline, formatBytes(used)) else null
    val remainingText = if (hasLimit) {
        stringResource(R.string.subscription_usage_remaining_inline, formatBytes(((total ?: 0L) - used).coerceAtLeast(0L)))
    } else {
        null
    }
    val noDataText = if (!hasQuota) stringResource(R.string.subscription_usage_no_data) else null

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_storage_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.subscription_usage_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                UsageBar(
                    fraction = animatedFraction,
                    startText = usedText ?: noDataText,
                    endText = remainingText,
                    modifier = Modifier.weight(1f)
                )
                RefreshButton(refreshing = refreshing, onClick = onRefresh)
            }

            val expireSeconds = usage?.trafficExpireEpochSeconds
            if (hasLimit && expireSeconds != null && expireSeconds > 0) {
                Text(
                    text = stringResource(
                        R.string.subscription_usage_expire,
                        com.v2ray.ang.util.Utils.formatTimestamp(expireSeconds * 1000)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/**
 * The two-tone bar. The label row is drawn twice at identical positions: once in a color that
 * is readable on the bright fill, clipped to the filled part, and once in a color readable on
 * the dark track, clipped to the unfilled part. A label that straddles the boundary (very low or
 * very high usage) therefore stays legible on both sides.
 */
@Composable
private fun UsageBar(
    fraction: Float,
    startText: String?,
    endText: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    val pill = RoundedCornerShape(50)
    val fillBrush = Brush.horizontalGradient(listOf(primary, lerp(primary, secondary, 0.6f)))
    val onFill = readableOn(lerp(primary, secondary, 0.3f))
    val track = lerp(colors.surfaceContainerHighest, primary, 0.16f)
    val onTrack = colors.onSurface
    val clamped = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(48.dp)
            .shadow(
                elevation = 4.dp,
                shape = pill,
                ambientColor = primary.copy(alpha = 0.2f),
                spotColor = primary.copy(alpha = 0.35f)
            )
            .border(width = 1.dp, brush = hudOutlineBrush(primary, strength = 0.7f), shape = pill)
            .padding(4.dp)
            .clip(pill)
            .background(track)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .clip(pill)
                .background(fillBrush)
        )
        UsageLabels(
            startText = startText,
            endText = endText,
            color = onFill,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(left = 0f, right = size.width * clamped) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
        UsageLabels(
            startText = startText,
            endText = endText,
            color = onTrack,
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawWithContent {
                    clipRect(left = size.width * clamped, right = size.width) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
    }
}

@Composable
private fun UsageLabels(
    startText: String?,
    endText: String?,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (startText != null) {
            UsageLabel(text = startText, color = color, modifier = Modifier.weight(1f, fill = false))
        }
        if (startText != null && endText != null) Spacer(Modifier.width(8.dp))
        if (endText != null) {
            UsageLabel(text = endText, color = color, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun UsageLabel(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/** Separate round button; sits to the right of the bar with its own gap and outline. */
@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "subscriptionUsageRefreshSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "subscriptionUsageRefreshSpinValue"
    )
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val description = stringResource(R.string.acc_refresh_subscription_usage)

    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = primary.copy(alpha = 0.2f),
                spotColor = primary.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = CircleShape)
            .clickable(enabled = !refreshing, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_24dp),
            contentDescription = description,
            tint = primary,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (refreshing) rotation else 0f)
        )
    }
}

/** Fraction of the quota consumed, 0..1. Zero when no positive limit is known (unlimited/no data). */
internal fun usageFraction(usedBytes: Long, totalBytes: Long?): Float =
    if (totalBytes != null && totalBytes > 0) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val rounded = (value * 100).roundToLong() / 100.0
    return if (unitIndex == 0) "${rounded.toLong()} ${units[unitIndex]}" else "%.2f %s".format(rounded, units[unitIndex])
}
