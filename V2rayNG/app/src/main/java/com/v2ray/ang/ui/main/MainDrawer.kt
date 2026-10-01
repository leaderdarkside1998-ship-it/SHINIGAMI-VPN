package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.hudControlFill
import com.v2ray.ang.ui.compose.hudOutlineBrush
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Shinigami(R.drawable.ic_shinigami_24dp, R.string.shinigami_title),
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Gaming(R.drawable.ic_gaming_24dp, R.string.title_gaming_mode),
    SelectGame(R.drawable.ic_play_24dp, R.string.title_select_game),
    Boost(R.drawable.ic_boost_24dp, R.string.title_boost_mode),
    Diagnostics(R.drawable.ic_about_24dp, R.string.title_diagnostics),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Info(R.drawable.ic_about_24dp, R.string.title_info)
}

private val primaryDrawerItems = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Gaming,
    MainDestination.SelectGame,
    MainDestination.Boost,
    MainDestination.Diagnostics,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings
)

private val drawerItems = primaryDrawerItems + listOf(
    MainDestination.Info
)

@Composable
fun MainDrawerContent(drawerState: DrawerState, onNavigate: (MainDestination) -> Unit) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.75f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
        ) {
            // SHINIGAMI VPN: wide banner image (the channel artwork). The app name
            // is already part of the artwork itself, so no text overlay is drawn
            // on top of it.
            Image(
                painter = painterResource(R.drawable.img_app_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            drawerItems.forEachIndexed { index, item ->
                if (index == 0) androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                if (index == primaryDrawerItems.size) HudDivider()
                HudMenuRow(
                    icon = item.iconRes,
                    label = stringResource(item.labelRes),
                    onClick = { onNavigate(item) }
                )
            }
        }
    }
}

/**
 * Drawer row in the same "HUD" look as the main screen (banner, ping pill, group tabs, bottom
 * controls): cut-corner card fading into a soft tint of the theme accent, thin illuminated
 * accent outline, accent-tinted shadow, round HUD icon bubble and a springy press-in scale.
 * Purely visual: the whole row is the single click target.
 */
@Composable
private fun HudMenuRow(icon: Int, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val base = colors.surfaceContainerHigh
    val shape = CutCornerShape(14.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hudMenuRowPressScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .scale(pressScale)
            .shadow(
                elevation = if (pressed) 2.dp else 5.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.25f),
                spotColor = primary.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(base, lerp(base, primary, 0.14f), base)))
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(hudControlFill(0.22f))
                .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward_24dp),
            contentDescription = null,
            tint = primary.copy(alpha = 0.75f),
            modifier = Modifier.size(15.dp)
        )
    }
}

/** Thin HUD separator: an accent line that fades out toward both edges. */
@Composable
private fun HudDivider() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, primary.copy(alpha = 0.55f), Color.Transparent)))
    )
}
