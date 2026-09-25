package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Row pinned above the group tabs showing the current group's subscription traffic quota
 * (from the `subscription-userinfo` header captured on the last update). Deliberately frameless
 * -- no card background/border/shadow -- so it reads as part of the page rather than a boxed
 * widget. The quota is summarized as a single circular percentage gauge; the raw used/remaining
 * byte counts sit underneath as plain text rather than being baked into a bar. Renders a quiet
 * "no data yet" row instead of the gauge when the subscription has never reported quota info --
 * most panels do, but plenty of private/self hosted ones never send the header at all, and
 * that's a normal, unremarkable state here.
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
    val hasBar = hasQuota && total != null && total > 0
    val fraction = if (total != null && total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 100f),
        label = "subscriptionUsageFraction"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.subscription_usage_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        AnimatedVisibility(visible = !hasQuota, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.subscription_usage_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RefreshButton(refreshing = refreshing, onClick = onRefresh)
            }
        }

        AnimatedVisibility(visible = hasQuota && !hasBar, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.subscription_usage_used_inline, formatBytes(used)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RefreshButton(refreshing = refreshing, onClick = onRefresh)
            }
        }

        AnimatedVisibility(visible = hasBar, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(top = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RefreshButton(refreshing = refreshing, onClick = onRefresh)
                    PercentGauge(fraction = animatedFraction)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.subscription_usage_used_inline, formatBytes(used)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.subscription_usage_remaining_inline,
                            formatBytes(((total ?: 0L) - used).coerceAtLeast(0L))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val expireSeconds = usage?.trafficExpireEpochSeconds
                if (expireSeconds != null && expireSeconds > 0) {
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
}

/**
 * Compact circular gauge: a thin ring that fills proportionally to [fraction], with the
 * percentage set inside as clean, bold, gradient-tinted numerals. This is the only thing
 * carrying the usage number now -- no bar, no baked-in used/remaining strings, no card chrome
 * around it, just the ring and the number.
 */
@Composable
private fun PercentGauge(fraction: Float, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val diameter = 46.dp
    val stroke = 4.dp

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2f, strokePx / 2f)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(primary, secondary, primary)),
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(fraction * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RowScope.RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
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
    val description = stringResource(R.string.acc_refresh_subscription_usage)
    IconButton(
        onClick = onClick,
        enabled = !refreshing,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_24dp),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(17.dp)
                .rotate(if (refreshing) rotation else 0f)
        )
    }
}

private fun formatBytes(bytes: Long): String {
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
