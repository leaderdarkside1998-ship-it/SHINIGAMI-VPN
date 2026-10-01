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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.hudControlFill
import com.v2ray.ang.ui.compose.hudOutlineBrush
import kotlinx.coroutines.delay

/**
 * Bottom area of the main screen, in the same "HUD" look as the top banner and server cards:
 *
 * 1. A controls row (AI button + power control / session timer) pushed to the physical right
 *    edge. The controls are free-standing -- no card or frame wraps them.
 * 2. The ping-test pill underneath. Tapping it tests the current server.
 *
 * There is deliberately no "Connected" / "Not connected" text down here; the power control
 * and the timer already show the state.
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
        // Pinned to the physical right in every language: in an RTL locale "End" would otherwise
        // flip the whole group to the left edge.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ControlsEndPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                AiCircleButton(onClick = onAiClick)
                Spacer(modifier = Modifier.width(ControlsSpacing))
                if (isRunning) {
                    StopTimerPill(
                        connectedSinceMillis = connectedSinceMillis,
                        onClick = { onAction(MainAction.ToggleService) }
                    )
                } else {
                    PowerFab(onClick = { onAction(MainAction.ToggleService) })
                }
            }
        }

        TestStatusBar(
            displayText = displayText,
            onClick = { onAction(MainAction.TestCurrentServer) }
        )
    }
}

private val PowerButtonSize = 44.dp
private val AiButtonSize = 32.dp
private val StopPillHeight = 32.dp
private val ControlsEndPadding = 14.dp
private val ControlsSpacing = 10.dp

/** Small round button, labeled "AI", that opens the SHINIGAMI AI assistant screen. */
@Composable
private fun AiCircleButton(onClick: () -> Unit) {
    val aiDescription = stringResource(R.string.acc_shinigami_ai)
    val primary = MaterialTheme.colorScheme.primary
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
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = primary.copy(alpha = 0.25f),
                spotColor = primary.copy(alpha = 0.45f)
            )
            .clip(CircleShape)
            .background(hudControlFill(0.22f))
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = aiDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.shinigami_ai_button_label),
            color = primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

/**
 * The start/connect control: a compact round HUD key -- accent-tinted surface, illuminated
 * outline, accent play glyph -- with a slow breathing halo while idle inviting the tap.
 */
@Composable
private fun PowerFab(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "powerFabPressScale"
    )
    val haloTransition = rememberInfiniteTransition(label = "powerFabHalo")
    val haloScale by haloTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "powerFabHaloScale"
    )
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.22f,
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
                .size(PowerButtonSize)
                .scale(haloScale)
                .clip(CircleShape)
                .background(primary.copy(alpha = haloAlpha))
        )
        Box(
            modifier = Modifier
                .size(PowerButtonSize)
                .scale(pressScale)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = primary.copy(alpha = 0.3f),
                    spotColor = primary.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(hudControlFill(0.30f))
                .border(width = 1.2.dp, brush = hudOutlineBrush(primary), shape = CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play_24dp),
                contentDescription = stringResource(R.string.acc_start),
                tint = primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Compact HUD pill: stop square + "HH:MM:SS" session counter (follows the theme color). */
@Composable
private fun StopTimerPill(connectedSinceMillis: Long?, onClick: () -> Unit) {
    // Fall back to the moment the pill first appeared if the service has not reported a start time.
    val fallbackStart = remember { System.currentTimeMillis() }
    val since = connectedSinceMillis ?: fallbackStart
    val stopDescription = stringResource(R.string.acc_stop)
    val primary = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "stopPillPressScale"
    )
    val shape = CutCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .height(StopPillHeight)
            .scale(pressScale)
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.25f),
                spotColor = primary.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(hudControlFill(0.30f))
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = stopDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primary)
        )
        ConnectionTimerText(
            connectedSinceMillis = since,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * Ping-test pill, styled exactly like the top banner (pointed ends, theme-tinted fill, thin
 * illuminated accent outline) so the two read as one family. Tapping it tests the currently
 * selected server.
 */
@Composable
private fun TestStatusBar(displayText: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "testStatusBarPressScale"
    )
    val shape = CutCornerShape(percent = 50)
    val base = colors.surfaceContainerHigh
    val background = Brush.horizontalGradient(listOf(base, lerp(base, primary, 0.14f), base))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
            .height(38.dp)
            .scale(pressScale)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.25f),
                spotColor = primary.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(background)
            .border(width = 1.dp, brush = hudOutlineBrush(primary), shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bolt_24dp),
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = displayText }
        )
    }
}
