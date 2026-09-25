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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.Utils
import kotlin.math.roundToLong

/**
 * Card pinned above the group tabs showing the current group's subscription traffic quota
 * (from the `subscription-userinfo` header captured on the last update). All the actual
 * numbers (used / remaining) are rendered *inside* the progress bar itself, baked into
 * whichever colored segment they belong to -- the bold/filled segment carries the used
 * amount, the faint/track segment carries what's left. Renders a quiet "no data yet" row
 * instead of the bar when the subscription has never reported quota info -- most panels do,
 * but plenty of private/self hosted ones never send the header at all, and that's a normal,
 * unremarkable state here.
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "subscriptionUsageFraction"
    )

    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = primary.copy(alpha = 0.22f),
                spotColor = primary.copy(alpha = 0.28f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.subscription_usage_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            RefreshButton(refreshing = refreshing, onClick = onRefresh)
        }

        AnimatedVisibility(visible = !hasQuota, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = stringResource(R.string.subscription_usage_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        AnimatedVisibility(visible = hasQuota && !hasBar, enter = fadeIn(), exit = fadeOut()) {
            UsageOnlyPill(
                text = stringResource(R.string.subscription_usage_used_inline, formatBytes(used)),
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        AnimatedVisibility(visible = hasBar, enter = fadeIn(), exit = fadeOut()) {
            Column {
                UsageBar(
                    fraction = animatedFraction,
                    usedText = stringResource(R.string.subscription_usage_used_inline, formatBytes(used)),
                    remainingText = stringResource(
                        R.string.subscription_usage_remaining_inline,
                        formatBytes(((total ?: 0L) - used).coerceAtLeast(0L))
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )

                val expireSeconds = usage?.trafficExpireEpochSeconds
                if (expireSeconds != null && expireSeconds > 0) {
                    Text(
                        text = stringResource(
                            R.string.subscription_usage_expire,
                            Utils.formatTimestamp(expireSeconds * 1000)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * The bar itself: one pill split into two colored segments whose widths track [fraction].
 * Each segment carries its own number baked directly into it -- there's nothing about the
 * usage rendered outside the pill. A subtle glossy highlight animates across the filled
 * segment for a soft, three-dimensional feel; the whole pill also casts a small drop shadow.
 */
@Composable
private fun UsageBar(
    fraction: Float,
    usedText: String,
    remainingText: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val glowTransition = rememberInfiniteTransition(label = "usageBarGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "usageBarGlowAlpha"
    )

    // Weight can't be zero, and a segment holding ~0% still deserves a sliver of color so the
    // pill always visibly reads as two parts.
    val usedWeight = fraction.coerceIn(0.045f, 0.955f)
    val remainingWeight = 1f - usedWeight
    val minTextWidth = 58.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(17.dp),
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(17.dp))
            .background(trackColor)
    ) {
        val usedWidth = maxWidth * usedWeight
        val remainingWidth = maxWidth * remainingWeight

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(usedWeight)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(primary, secondary))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(13.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent)
                            )
                        )
                )
                if (usedWidth > minTextWidth) {
                    Text(
                        text = usedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(remainingWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (remainingWidth > minTextWidth) {
                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/** Fallback for the (rare) case a panel reports bytes used but never sends a quota total. */
@Composable
private fun UsageOnlyPill(text: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .shadow(3.dp, RoundedCornerShape(17.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.horizontalGradient(listOf(primary, secondary))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
