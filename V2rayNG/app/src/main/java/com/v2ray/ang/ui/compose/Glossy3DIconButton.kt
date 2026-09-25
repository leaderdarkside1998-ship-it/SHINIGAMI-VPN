package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * A small round "glossy" action button that gives a flat vector icon a
 * professional, slightly 3D look: a diagonal gradient body, a soft drop
 * shadow, and a light highlight arc across the top-left — like a glass
 * or brushed-metal button instead of a flat icon.
 *
 * Used for the top-bar action row (ping/flash, theme, search, add,
 * more, close, and the drawer toggle itself) so the whole group reads
 * as one cohesive, polished control rather than plain flat icons.
 */
@Composable
fun Glossy3DIconButton(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val base = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val top = lighten(base, 0.32f)
    val bottom = darken(base, 0.28f)
    val iconTint = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(top, base, bottom),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Soft highlight sheen near the top-left to fake a glossy/3D surface.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.value * 0.32f, size.value * 0.28f),
                        radius = size.value * 0.6f
                    )
                )
        )
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

internal fun lighten(color: Color, factor: Float): Color = Color(
    red = color.red + (1f - color.red) * factor,
    green = color.green + (1f - color.green) * factor,
    blue = color.blue + (1f - color.blue) * factor,
    alpha = color.alpha
)

internal fun darken(color: Color, factor: Float): Color = Color(
    red = color.red * (1f - factor),
    green = color.green * (1f - factor),
    blue = color.blue * (1f - factor),
    alpha = color.alpha
)

/**
 * A "raised card" row used for the main drawer menu (اشتراک‌ها، تنظیمات به تفکیک
 * برنامه، حالت گیمینگ، ...): each entry gets its own elevated, gradient-filled
 * rounded card with a glossy icon bubble, instead of a flat NavigationDrawerItem
 * row. Same options, same order, same icons/labels — only the depth/finish changes.
 */
@Composable
fun Glossy3DMenuRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val base = colors.surfaceContainerHigh
    val top = lighten(base, 0.14f)
    val bottom = darken(base, 0.10f)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .shadow(
                elevation = 7.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(
                width = 0.7.dp,
                color = if (accent) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glossy3DIconButton(
            icon = icon,
            contentDescription = null,
            size = 38.dp,
            accent = accent,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward_24dp),
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}
