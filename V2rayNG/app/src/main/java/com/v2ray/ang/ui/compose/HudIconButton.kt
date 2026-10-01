package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small round action button in the same "HUD" look as the bottom controls of the main screen
 * (AI button / power key): accent-tinted surface, thin illuminated accent outline, accent
 * glyph, accent-tinted shadow and a springy press-in scale. Everything follows the active theme.
 *
 * Used for the top-bar action row (ping/flash, theme, search, add, more, close and the
 * collapsed toggle badge) so the top and bottom of the main screen read as one family.
 * [accent] strengthens the tint for the one primary badge.
 */
@Composable
fun HudIconButton(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hudIconButtonPressScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .shadow(
                elevation = if (pressed) 2.dp else 4.dp,
                shape = CircleShape,
                ambientColor = primary.copy(alpha = 0.25f),
                spotColor = primary.copy(alpha = 0.45f)
            )
            .clip(CircleShape)
            .background(hudControlFill(if (accent) 0.30f else 0.22f))
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = primary,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
