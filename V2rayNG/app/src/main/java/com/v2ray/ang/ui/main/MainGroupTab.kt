package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.compose.hudOutlineBrush
import com.v2ray.ang.ui.compose.readableOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Row of subscription/group chips ("import sub (108)", "Default (0)", ...). Each chip is a
 * chamfered HUD-style button with a leading icon, a centered label and a trailing chevron:
 * the selected one lifts with a spring-driven scale + accent glow and fills with a restrained
 * primary/secondary gradient, everything else settles back to a dark, understated resting
 * state with a subtle outline. Colors come from the active theme. With two groups the chips
 * share the width equally; with more they keep a fixed width and the row scrolls. Scrolls the
 * selected chip into view whenever selection changes (e.g. from swiping the pager).
 */
@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, groups.size) {
        if (selectedIndex in groups.indices) {
            scope.launch { listState.animateScrollToItem(selectedIndex) }
        }
    }

    val horizontalPadding = 14.dp
    val spacing = 12.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sharedWidth: Dp? = if (groups.size <= 2) {
            (maxWidth - horizontalPadding * 2 - spacing * (groups.size - 1)) / groups.size
        } else {
            null
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            itemsIndexed(groups, key = { _, group -> group.id.ifEmpty { "group-default" } }) { index, group ->
                val serverFlow = remember(group.id, mainViewModel) {
                    mainViewModel.serversForGroup(group.id)
                }
                GroupTabChip(
                    group = group,
                    selected = index == selectedIndex,
                    serverFlow = serverFlow,
                    fixedWidth = sharedWidth,
                    onClick = { onTabClick(index) }
                )
            }
        }
    }
}

@Composable
private fun GroupTabChip(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    fixedWidth: Dp?,
    onClick: () -> Unit
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }

    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    val shape = CutCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "groupTabScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "groupTabElevation"
    )
    val selectedFill = Brush.horizontalGradient(
        listOf(primary.copy(alpha = 0.92f), lerp(primary, secondary, 0.7f).copy(alpha = 0.92f))
    )
    val selectedForeground = readableOn(lerp(primary, secondary, 0.35f))
    val restingBackground by animateColorAsState(
        targetValue = colors.surfaceContainerHigh,
        label = "groupTabBackground"
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) selectedForeground else colors.onSurface,
        label = "groupTabForeground"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) selectedForeground else primary,
        label = "groupTabIconTint"
    )

    val widthModifier = if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier.widthIn(min = 160.dp)

    Row(
        modifier = widthModifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.35f),
                spotColor = primary.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(if (selected) selectedFill else Brush.linearGradient(listOf(restingBackground, restingBackground)))
            .border(
                width = 1.dp,
                brush = if (selected) hudOutlineBrush(lerp(primary, androidx.compose.ui.graphics.Color.White, 0.35f)) else hudOutlineBrush(primary, strength = 0.55f),
                shape = shape
            )
            .height(48.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(if (group.id.isEmpty()) R.drawable.ic_list_doc_24dp else R.drawable.ic_layers_24dp),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = foreground,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward_24dp),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
