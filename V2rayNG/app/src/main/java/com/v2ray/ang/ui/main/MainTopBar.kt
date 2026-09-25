package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.Glossy3DIconButton
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.resolveDarkTheme
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
fun MainTopBar(
    isLoading: Boolean,
    isTesting: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    // Collapsed by default: the whole action group hides behind a single
    // toggle badge and slides open, drawer-style, on tap.
    var actionsExpanded by remember { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        navigationIcon = {
            if (showSearch) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back))
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = stringResource(R.string.acc_open_menu))
                }
            }
        },
        actions = {
            if (!showSearch) {
                // Collapsed state: one glossy 3D badge that opens the drawer.
                AnimatedVisibility(
                    visible = !actionsExpanded,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    Glossy3DIconButton(
                        icon = R.drawable.ic_actions_toggle_24dp,
                        contentDescription = stringResource(R.string.acc_more),
                        accent = true,
                        modifier = Modifier.padding(end = 4.dp)
                    ) { actionsExpanded = true }
                }

                // Expanded state: the drawer slides open to reveal every action,
                // ending with a small X badge that slides it shut again.
                AnimatedVisibility(
                    visible = actionsExpanded,
                    enter = expandHorizontally(
                        animationSpec = tween(320),
                        expandFrom = Alignment.End
                    ) + fadeIn(tween(280)),
                    exit = shrinkHorizontally(
                        animationSpec = tween(240),
                        shrinkTowards = Alignment.End
                    ) + fadeOut(tween(180))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pingAutoHide = LocalPingAutoHide.current
                        Glossy3DIconButton(
                            icon = if (pingAutoHide) R.drawable.ic_bolt_24dp else R.drawable.ic_flash_off_24dp,
                            contentDescription = stringResource(R.string.acc_toggle_ping_auto_hide),
                            modifier = Modifier.padding(horizontal = 3.dp)
                        ) { onAction(MainAction.TogglePingAutoHide) }

                        val isDarkTheme = resolveDarkTheme()
                        Glossy3DIconButton(
                            icon = if (isDarkTheme) R.drawable.ic_light_mode_24dp else R.drawable.ic_dark_mode_24dp,
                            contentDescription = stringResource(R.string.acc_toggle_theme),
                            modifier = Modifier.padding(horizontal = 3.dp)
                        ) { ThemeManager.setThemeMode(if (isDarkTheme) "1" else "2") }

                        Glossy3DIconButton(
                            icon = R.drawable.ic_search_24dp,
                            contentDescription = stringResource(R.string.acc_search),
                            modifier = Modifier.padding(horizontal = 3.dp)
                        ) { onSearchToggle(true) }

                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            Glossy3DIconButton(
                                icon = R.drawable.ic_add_24dp,
                                contentDescription = stringResource(R.string.acc_add),
                                modifier = Modifier.padding(horizontal = 3.dp)
                            ) { showImportMenu = true }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                                scrollState = importMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(importMenuScrollState)
                            ) {
                                ImportMenuContent(
                                    onAction = { action ->
                                        showImportMenu = false
                                        onAction(action)
                                    }
                                )
                            }
                        }

                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            Glossy3DIconButton(
                                icon = R.drawable.ic_more_vert_24dp,
                                contentDescription = stringResource(R.string.acc_more),
                                modifier = Modifier.padding(horizontal = 3.dp)
                            ) { showMenu = true }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                scrollState = moreMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(moreMenuScrollState)
                            ) {
                                MoreMenuContent { action ->
                                    showMenu = false
                                    onMoreMenuAction(action)
                                }
                            }
                        }

                        Glossy3DIconButton(
                            icon = R.drawable.ic_close_24dp,
                            contentDescription = stringResource(R.string.acc_back),
                            modifier = Modifier.padding(start = 3.dp, end = 4.dp)
                        ) { actionsExpanded = false }
                    }
                }
            } else {
                // While searching, keep add/more reachable without the drawer.
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add))
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                        scrollState = importMenuScrollState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .heightIn(max = maxMenuHeight)
                            .verticalScrollbar(importMenuScrollState)
                    ) {
                        ImportMenuContent(
                            onAction = { action ->
                                showImportMenu = false
                                onAction(action)
                            }
                        )
                    }
                }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.acc_more))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        scrollState = moreMenuScrollState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .heightIn(max = maxMenuHeight)
                            .verticalScrollbar(moreMenuScrollState)
                    ) {
                        MoreMenuContent { action ->
                            showMenu = false
                            onMoreMenuAction(action)
                        }
                    }
                }
            }
        }
    )
}
