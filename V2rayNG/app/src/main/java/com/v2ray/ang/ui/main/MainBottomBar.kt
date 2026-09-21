package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import kotlinx.coroutines.delay

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    connectedSinceMillis: Long?,
    onAction: (MainAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = displayText
                    }
                )
            }
        }
        if (isRunning) {
            // While running, a pill in the accent color that holds the stop square and the live start-time counter
            // takes the place of the start button, so tapping it stops the service. (The round
            // "test connection" button was removed; the bottom bar row still tests on tap.)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = ControlsEndPadding)
                    .offset(y = (-24).dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.End
            ) {
                StopTimerPill(
                    connectedSinceMillis = connectedSinceMillis,
                    onClick = { onAction(MainAction.ToggleService) }
                )
            }
        } else {
            FloatingActionButton(
                onClick = { onAction(MainAction.ToggleService) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = ControlsEndPadding)
                    .offset(y = (-28).dp)
                    .navigationBarsPadding()
                    .size(ControlFabSize),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_24dp),
                    contentDescription = stringResource(R.string.acc_start),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private val ControlFabSize = 56.dp
private val ControlsEndPadding = 16.dp

/** Pill in the app accent color: stop square + "HH:MM:SS" session counter (follows the theme color). */
@Composable
private fun StopTimerPill(connectedSinceMillis: Long?, onClick: () -> Unit) {
    // Fall back to the moment the pill first appeared if the service has not reported a start time.
    val fallbackStart = remember { System.currentTimeMillis() }
    val since = connectedSinceMillis ?: fallbackStart
    val stopDescription = stringResource(R.string.acc_stop)
    val pillColor = MaterialTheme.colorScheme.primary
    val onPillColor = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(pillColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .semantics { contentDescription = stopDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(onPillColor)
        )
        ConnectionTimerText(connectedSinceMillis = since, color = onPillColor)
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
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
    )
}
