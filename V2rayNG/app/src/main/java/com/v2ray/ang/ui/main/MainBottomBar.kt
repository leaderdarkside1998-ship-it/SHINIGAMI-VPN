package com.v2ray.ang.ui.main

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.darken
import com.v2ray.ang.ui.compose.lighten
import kotlinx.coroutines.delay

/**
 * Bottom area of the main screen. Three clearly separate pieces, stacked, never overlapping:
 *
 * 1. A centered connection headline ("Connected"/"Not connected") that reads like a proper VPN
 *    app status, not just a technical log line.
 * 2. A floating controls row (AI button + the power control) that sits above everything else.
 * 3. The status/test bar underneath -- a standalone 3D card. Tapping it tests the current
 *    server, same as before; it is purely a status strip, the power control never sits inside
 *    or on top of it any more.
 */
@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    connectedSinceMillis: Long?,
    onAction: (MainAction) -> Unit,
    onAiClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        ConnectionHeadline(isRunning = isRunning)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ControlsEndPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            AiCircleButton(onClick = onAiClick)
            Spacer(modifier = Modifier.width(AiButtonSpacing))
            if (isRunning) {
                StopTimerPill(
                    connectedSinceMillis = connectedSinceMillis,
                    onClick = { onAction(MainAction.ToggleService) }
                )
            } else {
                PowerFab(onClick = { onAction(MainAction.ToggleService) })
            }
        }

        TestStatusBar(
            displayText = displayText,
            onClick = { onAction(MainAction.TestCurrentServer) }
        )
    }
}

/**
 * Big, centered "Connected" / "Not connected" headline shown above the power control, in the
 * app accent color while connected and a muted tone otherwise -- the single-glance status cue
 * every mainstream VPN app leads with, distinct from the small tap-to-test strip below it.
 */
@Composable
private fun ConnectionHeadline(isRunning: Boolean) {
    val label = stringResource(
        if (isRunning) R.string.connection_connected else R.string.connection_not_connected
    )
    val color = if (isRunning) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isRunning) color else MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
    }
}

private val ControlFabSize = 76.dp
private val ControlsEndPadding = 14.dp
private val AiButtonSize = 30.dp
private val AiButtonSpacing = 10.dp

/** Small round button, labeled "AI", that opens the SHINIGAMI AI assistant screen. */
@Composable
private fun AiCircleButton(onClick: () -> Unit) {
    val aiDescription = stringResource(R.string.acc_shinigami_ai)
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "aiButtonPressScale"
    )
    Box(
        modifier = Modifier
            .size(AiButtonSize)
            .scale(pressScale)
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                ambientColor = secondary.copy(alpha = 0.5f),
                spotColor = secondary.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lighten(secondary, 0.12f), tertiary)))
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.18f))
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = aiDescription },
        contentAlignment = Alignment.Center
    ) {
        // Tight highlight sheen, top-left, for the same glossy finish as the other action dots.
        Box(
            modifier = Modifier
                .size(AiButtonSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(AiButtonSize.value * 0.30f, AiButtonSize.value * 0.26f),
                        radius = AiButtonSize.value * 0.45f
                    )
                )
        )
        Text(
            text = stringResource(R.string.shinigami_ai_button_label),
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp
        )
    }
}

/**
 * The start/connect control: a round, glossy, gradient-filled button -- a proper 3D power key.
 * Sized and haloed to read as the screen's main action (the way NordVPN/ExpressVPN-style apps
 * anchor around one big connect button), with a slow breathing halo while idle inviting the tap.
 */
@Composable
private fun PowerFab(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "powerFabPressScale"
    )
    val haloTransition = rememberInfiniteTransition(label = "powerFabHalo")
    val haloScale by haloTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerFabHaloScale"
    )
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerFabHaloAlpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(ControlFabSize)
                .scale(haloScale)
                .clip(CircleShape)
                .background(primary.copy(alpha = haloAlpha))
        )
        Box(
            modifier = Modifier
                .size(ControlFabSize)
                .scale(pressScale)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = primary.copy(alpha = 0.55f),
                    spotColor = primary.copy(alpha = 0.65f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, secondary)))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
        // Glossy top highlight for a rounded, three-dimensional key rather than a flat disc.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlFabSize / 2)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(topStart = ControlFabSize / 2, topEnd = ControlFabSize / 2))
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
                )
        )
        Icon(
            painter = painterResource(R.drawable.ic_play_24dp),
            contentDescription = stringResource(R.string.acc_start),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(34.dp)
        )
        }
    }
}

/** Pill in the app accent color: stop square + "HH:MM:SS" session counter (follows the theme color). */
@Composable
private fun StopTimerPill(connectedSinceMillis: Long?, onClick: () -> Unit) {
    // Fall back to the moment the pill first appeared if the service has not reported a start time.
    val fallbackStart = remember { System.currentTimeMillis() }
    val since = connectedSinceMillis ?: fallbackStart
    val stopDescription = stringResource(R.string.acc_stop)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPillColor = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "stopPillPressScale"
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .scale(pressScale)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.45f),
                spotColor = primary.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(lighten(primary, 0.08f), secondary)))
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.15f))
                ),
                shape = shape
            )
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = stopDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(onPillColor)
            )
            ConnectionTimerText(connectedSinceMillis = since, color = onPillColor)
        }
    }
}

/** Live "HH:MM:SS" elapsed since [connectedSinceMillis], ticking every second. */
@Composable
private fun ConnectionTimerText(connectedSinceMillis: Long, color: Color, modifier: Modifier = Modifier) {
    var elapsedSeconds by remember(connectedSinceMillis) {
        mutableLongStateOf(((System.currentTimeMillis() - connectedSinceMillis) / 1000).coerceAtLeast(0))
    }
    LaunchedEffect(connectedSinceMillis) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - connectedSinceMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val h = elapsedSeconds / 3600
    val m = (elapsedSeconds % 3600) / 60
    val sec = elapsedSeconds % 60
    Text(
        text = "%02d:%02d:%02d".format(h, m, sec),
        color = color,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * Standalone status/test card, fully separate from the floating controls above it. Tapping
 * anywhere on it tests the currently selected server. Styled as a lifted, slightly rounded
 * 3D card rather than a flush full-width strip, with a small bolt icon marking it as the
 * tap-to-test row, a soft top sheen, and a hairline rim for a more refined, less flat finish.
 */
@Composable
private fun TestStatusBar(displayText: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "testStatusBarPressScale"
    )
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val top = lighten(MaterialTheme.colorScheme.surfaceContainerHigh, 0.06f)
    val bottom = darken(MaterialTheme.colorScheme.surfaceContainer, 0.04f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .scale(pressScale)
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bolt_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = displayText }
            )
        }
    }
}
