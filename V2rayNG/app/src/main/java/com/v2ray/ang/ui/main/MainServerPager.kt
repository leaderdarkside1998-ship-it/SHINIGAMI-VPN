package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import com.v2ray.ang.ui.compose.ServerCardColors
import com.v2ray.ang.ui.compose.hudOutlineBrush
import com.v2ray.ang.ui.compose.serverCardAccent
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onTestServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
        onTestServer,
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
            testPing = onTestServer,
        )
    }
    ServerListPage(
        rows = groupState.rows,
        selectedGuid = selectedGuid,
        locateTarget = locateTarget?.takeIf { it.groupId == groupId },
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        actions = actions,
        onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
        onMoveServer = { fromIndex, toIndex ->
            mainViewModel.moveServer(groupId, fromIndex, toIndex)
        },
        contentPadding = contentPadding
    )
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String) -> Unit,
    val testPing: (String) -> Unit,
)

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ServerRowActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        doubleColumnDisplay = true,
                        actions = actions
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                row = row,
                                isSelected = row.guid == selectedGuid,
                                actions = actions
                            )
                        }
                    }
                } else {
                    ServerItemRow(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun ServerItemRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        doubleColumnDisplay = false,
        actions = actions
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    Column {
        ServerListItem(
            row = row,
            isSelected = isSelected,
            doubleColumnDisplay = doubleColumnDisplay,
            actions = actions
        )
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    val testResult = if (row.testDelayMillis == 0L) {
        ""
    } else {
        stringResource(R.string.server_test_delay_value, row.testDelayMillis)
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }

    // Professional, subtle "pop in" entrance for each server card: fades and scales up once
    // when it first appears (e.g. on load, search, or reorder), rather than snapping in.
    val entrance = remember(row.guid) { Animatable(0f) }
    LaunchedEffect(row.guid) {
        entrance.snapTo(0f)
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        )
    }

    // HUD card: the surface and every text color are fixed (dark navy + explicit high-contrast
    // text) so NO theme can make the name/address/protocol unreadable. The active theme only
    // drives small accents (border, check badge, protocol tag tint, bolt icon, glow).
    val accent = serverCardAccent(MaterialTheme.colorScheme.primary)
    val compact = doubleColumnDisplay || LocalConfiguration.current.screenWidthDp < 360
    val cardShape = RoundedCornerShape(14.dp)

    val topTint by animateColorAsState(
        targetValue = if (isSelected) {
            androidx.compose.ui.graphics.lerp(ServerCardColors.NavySelectedTop, accent, 0.05f)
        } else ServerCardColors.NavyTop,
        label = "server_card_top"
    )
    val bottomTint by animateColorAsState(
        targetValue = if (isSelected) ServerCardColors.NavySelectedBottom else ServerCardColors.NavyBottom,
        label = "server_card_bottom"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accent else ServerCardColors.BorderIdle,
        label = "server_card_border"
    )
    val glowColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f),
        label = "server_card_glow"
    )
    val borderBrush: Brush = if (isSelected) hudOutlineBrush(borderColor) else SolidColor(borderColor)

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = entrance.value
                val scale = 0.94f + entrance.value * 0.06f
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isSelected) 8.dp else 2.dp,
                shape = cardShape,
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(topTint, bottomTint)))
            .serverCardHudDecor(accent = accent, isSelected = isSelected)
            // Same 1dp stroke in both states so selected and unselected cards stay the same size.
            .border(BorderStroke(1.dp, borderBrush), cardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .semantics {
                    if (selectedStateDescription != null) {
                        stateDescription = selectedStateDescription
                    }
                }
                .clickable { actions.select(row.guid) },
            verticalAlignment = Alignment.Top
        ) {
            // Selection indicator: filled accent badge with a check when selected, a quiet hollow
            // ring when not. Identical 18dp footprint in both states.
            Box(
                Modifier
                    .padding(start = if (compact) 8.dp else 12.dp, end = if (compact) 6.dp else 8.dp, top = if (compact) 11.dp else 12.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(accent.copy(alpha = 0.18f))
                        else Modifier
                    )
                    .border(
                        BorderStroke(if (isSelected) 1.2.dp else 1.dp, if (isSelected) accent else ServerCardColors.IndicatorIdle),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        painterResource(R.drawable.ic_fab_check),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = accent
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(
                        end = if (compact) 6.dp else 10.dp,
                        top = if (compact) 8.dp else 9.dp,
                        bottom = if (compact) 8.dp else 10.dp
                    )
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.remarks,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = if (compact) 13.sp else 14.sp,
                            lineHeight = if (compact) 17.sp else 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.sp,
                            lineBreak = LineBreak.Paragraph
                        ),
                        color = ServerCardColors.TextName,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val iconTint = ServerCardColors.IconMuted
                    if (doubleColumnDisplay) {
                        IconButton(onClick = { actions.more(row.guid, row.profile) }, Modifier.size(28.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                stringResource(R.string.acc_more),
                                Modifier.size(17.dp),
                                tint = iconTint
                            )
                        }
                    } else {
                        IconButton(onClick = { actions.share(row.guid, row.profile) }, Modifier.size(28.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_share_24dp),
                                stringResource(R.string.title_configuration_share),
                                Modifier.size(16.dp),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = { actions.edit(row.guid, row.profile) }, Modifier.size(28.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_edit_24dp),
                                stringResource(R.string.acc_edit),
                                Modifier.size(16.dp),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = { actions.remove(row.guid) }, Modifier.size(28.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                stringResource(R.string.acc_delete),
                                Modifier.size(16.dp),
                                tint = iconTint
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (row.subscriptionBadge.isNotBlank()) {
                        Box(
                            Modifier
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f)), Alignment.Center
                        ) {
                            Text(row.subscriptionBadge.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = accent)
                        }
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        row.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = if (compact) 11.sp else 11.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = ServerCardColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Protocol shown as a quiet accent-tinted tag; text color is fixed for contrast.
                    Text(
                        row.typeDescription,
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(BorderStroke(0.5.dp, accent.copy(alpha = 0.28f)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 13.sp, letterSpacing = 0.4.sp),
                        color = ServerCardColors.TextTag,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PingSlot(
                        guid = row.guid,
                        delayMillis = row.testDelayMillis,
                        resultText = testResult,
                        accent = accent,
                        onTest = { actions.testPing(row.guid) }
                    )
                }
            }
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}

/**
 * Global toggle for the ping display behavior (set from the bolt/flash-off button in the top
 * bar and persisted via [MainAction.TogglePingAutoHide]). true (default): a fresh ping result
 * shows for 5 minutes then the slot reverts to the bolt icon. false: once a server has a result,
 * the number stays shown permanently and the slot never reverts to the bolt icon.
 */
internal val LocalPingAutoHide = compositionLocalOf { true }

/** Safety net: stop the "testing" pulse if a result never arrives (cancelled / failed to start). */
private const val PING_TEST_TIMEOUT_MS = 20000L

/**
 * Ping slot of a server card. Idle it shows a bolt icon (like the FL proxies screen); tapping it
 * starts a test and the bolt pulses while it runs; the result (single or group test) is then shown
 * for 5 minutes and the slot goes back to the bolt so it can be tapped again.
 */
@Composable
private fun PingSlot(
    guid: String,
    delayMillis: Long,
    resultText: String,
    accent: Color,
    onTest: () -> Unit,
) {
    var testing by remember { mutableStateOf(false) }
    val pingAutoHide = LocalPingAutoHide.current

    // The arrival time of a result is stamped by the ViewModel when the result is applied, for
    // every server whether or not its card is on screen ([PingResultClock]). Here it is only
    // filled in for a value that has no stamp yet (one restored from storage at app start), so
    // that the 5-minute countdown below has a starting point.
    LaunchedEffect(guid, delayMillis) {
        // A reset to 0 happens at the START of a test, so it must not stop the "testing" pulse;
        // only an actual result (or the timeout below) ends it.
        if (delayMillis != 0L) {
            PingResultClock.recordIfAbsent(guid)
            testing = false
        }
    }
    LaunchedEffect(testing) {
        if (testing) {
            delay(PING_TEST_TIMEOUT_MS)
            testing = false
        }
    }

    // "now" only needs to tick while auto-hide is on and a result is waiting to expire, so the
    // countdown actually counts down instead of being computed once and frozen.
    var now by remember { mutableLongStateOf(PingResultClock.now()) }
    LaunchedEffect(pingAutoHide, delayMillis, guid) {
        // Always refresh first: while auto-hide was off "now" was frozen, so turning it back on
        // would otherwise judge old results against a stale clock for a moment.
        now = PingResultClock.now()
        if (!pingAutoHide || delayMillis == 0L) return@LaunchedEffect
        // Tick until the result has expired, then stop (no endless 1-second loop afterwards).
        while (
            PingResultClock.isResultVisible(
                delayMillis = delayMillis,
                pingAutoHide = true,
                arrivedAt = PingResultClock.arrivedAt(guid),
                now = now,
            )
        ) {
            delay(1000)
            now = PingResultClock.now()
        }
    }

    // Single source of truth for visibility, recomputed straight from pingAutoHide every time it
    // changes. With auto-hide off this is unconditionally true the moment a result exists -
    // nothing else has to happen first, so the power/flash button takes effect immediately and
    // even reveals a result that was already stored before auto-hide was turned off.
    val showResult by remember(guid, delayMillis, pingAutoHide) {
        derivedStateOf {
            !testing && PingResultClock.isResultVisible(
                delayMillis = delayMillis,
                pingAutoHide = pingAutoHide,
                arrivedAt = PingResultClock.arrivedAt(guid),
                now = now,
            )
        }
    }

    val pulse = rememberInfiniteTransition(label = "ping_pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "ping_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .height(24.dp)
            .defaultMinSize(minWidth = 32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                testing = true
                onTest()
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        if (showResult && delayMillis != 0L) {
            Text(
                resultText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Light),
                color = if (delayMillis < 0L) colorPingRed else ServerCardColors.PingGood,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_bolt_24dp),
                contentDescription = stringResource(R.string.connection_test_pending),
                tint = accent,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = if (testing) pulseAlpha else 1f }
            )
        }
    }
}

/**
 * Decorative "HUD" layer painted on top of the card's navy gradient and under its content:
 * soft accent glows in two corners, a faint diagonal hatch, a lit top edge and small
 * corner brackets. It only draws (no layout, no state) and is cached per size, so it costs
 * nothing while scrolling. Selected cards get a stronger version of the same pattern.
 */
private fun Modifier.serverCardHudDecor(accent: Color, isSelected: Boolean): Modifier =
    drawWithCache {
        val w = size.width
        val h = size.height
        val glowStrength = if (isSelected) 1f else 0.55f

        // Main glow from the top-end corner (where the name sits in RTL), second cooler glow
        // from the bottom-start corner so the card never looks like a flat slab.
        val glowTopEnd = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.26f * glowStrength), Color.Transparent),
            center = Offset(w, 0f),
            radius = w * 0.85f
        )
        val glowBottomStart = Brush.radialGradient(
            colors = listOf(Color(0xFF4F7CFF).copy(alpha = 0.16f * glowStrength), Color.Transparent),
            center = Offset(0f, h),
            radius = w * 0.75f
        )
        val topEdge = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                accent.copy(alpha = if (isSelected) 0.85f else 0.45f),
                Color.Transparent
            )
        )

        val hatchStep = 9.dp.toPx()
        val hatchColor = Color.White.copy(alpha = 0.035f)
        val hatchWidth = 0.8.dp.toPx()
        val bracket = 7.dp.toPx()
        val inset = 4.dp.toPx()
        val bracketColor = accent.copy(alpha = if (isSelected) 0.75f else 0.32f)
        val bracketWidth = 1.2.dp.toPx()

        onDrawBehind {
            drawRect(glowTopEnd)
            drawRect(glowBottomStart)

            // Diagonal hatch (45 degrees), clipped by the card's own clip.
            var x = -h
            while (x < w) {
                drawLine(
                    color = hatchColor,
                    start = Offset(x, h),
                    end = Offset(x + h, 0f),
                    strokeWidth = hatchWidth
                )
                x += hatchStep
            }

            // Lit top edge.
            drawLine(
                brush = topEdge,
                start = Offset(w * 0.08f, 0.5.dp.toPx()),
                end = Offset(w * 0.92f, 0.5.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Corner brackets on the two corners that are free of content (the selection ring
            // sits top-end, the protocol tag bottom-start): top-start and bottom-end.
            drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracket, inset), bracketWidth, StrokeCap.Round)
            drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + bracket), bracketWidth, StrokeCap.Round)
            drawLine(bracketColor, Offset(w - inset - bracket, h - inset), Offset(w - inset, h - inset), bracketWidth, StrokeCap.Round)
            drawLine(bracketColor, Offset(w - inset, h - inset - bracket), Offset(w - inset, h - inset), bracketWidth, StrokeCap.Round)
        }
    }
