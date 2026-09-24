package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.Utils
import kotlin.math.roundToLong

/**
 * Slim card pinned above the group tabs showing the current group's subscription traffic quota
 * (from the `subscription-userinfo` header captured on the last update) with a thin, animated
 * progress fill and a refresh action. Renders a quiet "no data yet" row instead of the bar when
 * the subscription has never reported quota info -- most panels do, but plenty of private/self
 * hosted ones never send the header at all, and that's a normal, unremarkable state here.
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
    val fraction = if (total != null && total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "subscriptionUsageFraction"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (hasQuota) formatUsageLine(used, total) else stringResource(R.string.subscription_usage_no_data),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            RefreshButton(refreshing = refreshing, onClick = onRefresh)
        }

        AnimatedVisibility(visible = hasQuota, enter = fadeIn(), exit = fadeOut()) {
            Column {
                if (total != null && total > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                val expireSeconds = usage?.trafficExpireEpochSeconds
                if (expireSeconds != null && expireSeconds > 0) {
                    Text(
                        text = stringResource(
                            R.string.subscription_usage_expire,
                            Utils.formatTimestamp(expireSeconds * 1000)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
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
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_24dp),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (refreshing) rotation else 0f)
        )
    }
}

private fun formatUsageLine(usedBytes: Long, totalBytes: Long?): String {
    val used = formatBytes(usedBytes)
    return if (totalBytes != null && totalBytes > 0) "$used / ${formatBytes(totalBytes)}" else used
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
