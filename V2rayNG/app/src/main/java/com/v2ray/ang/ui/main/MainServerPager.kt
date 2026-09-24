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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    // Flat, hairline-outlined card. Selection is shown by a thin accent line, a soft accent tint and a
    // slightly stronger outline instead of a heavy bar and shadow.
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary.copy(alpha = 0.07f).compositeOver(colors.surfaceContainerLow) else colors.surfaceContainerLow,
        label = "server_card_container"
    )
    val outlineColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.45f),
        label = "server_card_outline"
    )

    Card(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .graphicsLayer {
                alpha = entrance.value
                val scale = 0.94f + entrance.value * 0.06f
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(if (isSelected) 1.dp else 0.5.dp, outlineColor)
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
                .clickable { actions.select(row.guid) }
        ) {
            // Slim selection line (2dp, rounded ends) at the leading edge.
            Box(
                Modifier
                    .width(9.dp)
                    .fillMaxHeight()
            ) {
                if (isSelected) {
                    Box(
                        Modifier
                            .padding(start = 5.dp, top = 13.dp, bottom = 13.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.dp))
                            .background(colors.primary)
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.remarks,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 0.1.sp,
                            lineBreak = LineBreak.Paragraph
                        ),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val iconTint = colors.onSurfaceVariant.copy(alpha = 0.75f)
                    if (doubleColumnDisplay) {
                        IconButton(onClick = { actions.more(row.guid, row.profile) }, Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                stringResource(R.string.acc_more),
                                Modifier.size(20.dp),
                                tint = iconTint
                            )
                        }
                    } else {
                        IconButton(onClick = { actions.share(row.guid, row.profile) }, Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_share_24dp),
                                stringResource(R.string.title_configuration_share),
                                Modifier.size(19.dp),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = { actions.edit(row.guid, row.profile) }, Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_edit_24dp),
                                stringResource(R.string.acc_edit),
                                Modifier.size(19.dp),
                                tint = iconTint
                            )
                        }
                        IconButton(onClick = { actions.remove(row.guid) }, Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                stringResource(R.string.acc_delete),
                                Modifier.size(19.dp),
                                tint = iconTint
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (row.subscriptionBadge.isNotBlank()) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.14f)), Alignment.Center
                        ) {
                            Text(row.subscriptionBadge.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colors.primary)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        row.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Light),
                        color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Protocol shown as a quiet tinted tag rather than loud orange text.
                    Text(
                        row.typeDescription,
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 7.dp, vertical = 1.5.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PingSlot(
                        guid = row.guid,
                        delayMillis = row.testDelayMillis,
                        resultText = testResult,
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
    onTest: () -> Unit,
) {
    var testing by remember { mutableStateOf(false) }
    val pingAutoHide = LocalPingAutoHide.current

    // The arrival time of a result is stamped by the ViewModel when the result is applied, for
    // every server whether or not its card is on screen ([PingResultClock]). Here it is only
    // filled in for a value that has no stamp yet (one restored from storage at app start), so
    // that the 5-minute countdown below has a starting point.
    LaunchedEffect(guid, delayMillis) {
        if (delayMillis != 0L) PingResultClock.recordIfAbsent(guid)
        testing = false
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
        if (!pingAutoHide || delayMillis == 0L) return@LaunchedEffect
        while (true) {
            now = PingResultClock.now()
            delay(1000)
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
                color = if (delayMillis < 0L) colorPingRed else MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_bolt_24dp),
                contentDescription = stringResource(R.string.connection_test_pending),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = if (testing) pulseAlpha else 1f }
            )
        }
    }
}
