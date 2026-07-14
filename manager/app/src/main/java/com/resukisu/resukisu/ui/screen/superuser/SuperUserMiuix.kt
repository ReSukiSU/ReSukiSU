package com.resukisu.resukisu.ui.screen.superuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel.AppGroup
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel.AppInfo
import com.resukisu.resukisu.ui.component.AppIconImage
import com.resukisu.resukisu.ui.component.ListPopupDefaults
import com.resukisu.resukisu.ui.component.ScrollToTopOnChange
import com.resukisu.resukisu.ui.component.SearchStatus
import com.resukisu.resukisu.ui.component.miuix.SearchBarFake
import com.resukisu.resukisu.ui.component.miuix.SearchBox
import com.resukisu.resukisu.ui.component.miuix.SearchPager
import com.resukisu.resukisu.ui.component.statustag.StatusTag
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.theme.isInDarkTheme
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.ownerNameForUid
import com.resukisu.resukisu.ui.util.rememberBlurBackdrop
import com.resukisu.resukisu.ui.viewmodel.AppSortType
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SuperUserPagerMiuix(
    uiState: com.resukisu.resukisu.ui.viewmodel.SuperUserUiState,
    onRefresh: () -> Unit,
    onOpenSulog: () -> Unit,
    onOpenProfile: (com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel.AppGroup) -> Unit,
    onToggleShowSystemApps: () -> Unit,
    onClearVmSearch: () -> Unit,
    bottomInnerPadding: Dp,
) {
    // Search / sort / primary-user filter live in the Miuix screen; showSystemApps stays on the
    // shared ViewModel (it re-filters appGroupList there). The ViewModel search is kept empty so
    // the main list stays full while the pager shows the locally-filtered results.
    val searchLabel = stringResource(R.string.search_apps)
    val groupByUid = remember(uiState.appGroupList) { uiState.appGroupList.associateBy { it.uid } }
    var rawSearchStatus by remember { mutableStateOf(SearchStatus(searchLabel)) }
    var sortConfig by remember { mutableStateOf(com.resukisu.resukisu.ui.viewmodel.AppSortConfig()) }
    var showOnlyPrimaryUserApps by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onClearVmSearch() }

    val base = uiState.appGroupList
    val primaryFiltered = remember(base, showOnlyPrimaryUserApps) {
        if (showOnlyPrimaryUserApps) base.filter { it.uid / 100000 == 0 } else base
    }
    val sorted = remember(primaryFiltered, sortConfig) {
        val cmp: Comparator<com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel.AppGroup> = when (sortConfig.sortType) {
            com.resukisu.resukisu.ui.viewmodel.AppSortType.NAME -> compareBy { it.mainApp.label.lowercase() }
            com.resukisu.resukisu.ui.viewmodel.AppSortType.PACKAGE_NAME -> compareBy { it.mainApp.packageName.lowercase() }
            com.resukisu.resukisu.ui.viewmodel.AppSortType.INSTALL_TIME -> compareBy { it.mainApp.packageInfo.firstInstallTime }
            com.resukisu.resukisu.ui.viewmodel.AppSortType.UPDATE_TIME -> compareBy { it.mainApp.packageInfo.lastUpdateTime }
        }
        primaryFiltered.sortedWith(if (sortConfig.reversed) cmp.reversed() else cmp)
    }
    val groupedApps = remember(sorted) { sorted.map { it.toTiannGroupedApps() } }
    val query = rawSearchStatus.searchText.trim()
    val searchResults = remember(sorted, query) {
        if (query.isEmpty()) emptyList()
        else sorted.mapNotNull { group ->
            val matched = group.apps.filter {
                it.label.contains(query, true) || it.packageName.contains(query, true)
            }
            if (matched.isEmpty()) null else group.toTiannGroupedApps(matched.map { it.packageName }.toSet())
        }
    }
    val recentlyInstalledResults = remember(base) {
        base.filter { it.isRecentlyInstalled }.map { it.toTiannGroupedApps() }
    }
    val userIds = remember(base) { base.map { it.uid / 100000 }.distinct().sorted() }
    val showSystemApps = uiState.showSystemApps
    val isRefreshing = uiState.isRefreshing
    val hasLoaded = true
    val searchStatus = rawSearchStatus.copy(
        resultStatus = when {
            query.isEmpty() -> SearchStatus.ResultStatus.DEFAULT
            searchResults.isEmpty() -> SearchStatus.ResultStatus.EMPTY
            else -> SearchStatus.ResultStatus.SHOW
        }
    )

    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.superuser),
                        navigationIcon = {
                            IconButton(
                                onClick = onOpenSulog,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Notes,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(R.string.settings_sulog)
                                )
                            }
                        },
                        actions = {
                            Box {
                                val showSortPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showSortPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showSortPopup.value = false },
                                    content = {
                                        ListPopupColumn {
                                            val sortEntries = listOf(
                                                AppSortType.NAME to R.string.sort_by_name,
                                                AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                                                AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                                                AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
                                            )
                                            val sortGroupSize = sortEntries.size + 1

                                            sortEntries.forEachIndexed { index, (type, resId) ->
                                                DropdownImpl(
                                                    text = stringResource(resId),
                                                    optionSize = sortGroupSize,
                                                    isSelected = sortConfig.sortType == type,
                                                    index = index,
                                                    onSelectedIndexChange = {
                                                        sortConfig = sortConfig.withType(type)
                                                        showSortPopup.value = false
                                                    }
                                                )
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                                thickness = 1.5.dp,
                                            )

                                            DropdownImpl(
                                                text = stringResource(R.string.sort_reverse),
                                                optionSize = sortGroupSize,
                                                isSelected = sortConfig.reversed,
                                                index = sortEntries.size,
                                                onSelectedIndexChange = {
                                                    sortConfig = sortConfig.toggleReversed()
                                                    showSortPopup.value = false
                                                }
                                            )
                                        }
                                    }
                                )

                                IconButton(
                                    onClick = { showSortPopup.value = true },
                                    holdDownState = showSortPopup.value,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        tint = colorScheme.onSurface,
                                        contentDescription = stringResource(R.string.menu_sort)
                                    )
                                }
                            }

                            Box {
                                val showTopPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showTopPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = {
                                        showTopPopup.value = false
                                    },
                                    content = {
                                        val isMultiUser = userIds.size > 1
                                        val size = if (isMultiUser) 2 else 1
                                        ListPopupColumn {
                                            DropdownImpl(
                                                text = stringResource(R.string.show_system_apps),
                                                isSelected = showSystemApps,
                                                optionSize = size,
                                                onSelectedIndexChange = {
                                                    onToggleShowSystemApps()
                                                    showTopPopup.value = false
                                                },
                                                index = 0
                                            )
                                            if (isMultiUser) {
                                                DropdownImpl(
                                                    text = stringResource(R.string.show_only_primary_user_apps),
                                                    isSelected = showOnlyPrimaryUserApps,
                                                    optionSize = size,
                                                    onSelectedIndexChange = {
                                                        showOnlyPrimaryUserApps = !showOnlyPrimaryUserApps
                                                        showTopPopup.value = false
                                                    },
                                                    index = 1
                                                )
                                            }
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        showTopPopup.value = true
                                    },
                                    holdDownState = showTopPopup.value
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.MoreCircle,
                                        tint = colorScheme.onSurface,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                rawSearchStatus = searchStatus.copy(offsetY = newOffsetY)
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    rawSearchStatus = searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }
            LaunchedEffect(searchResults) {
                expandedSearchUids.value = searchResults
                    .filter { it.apps.size > 1 }
                    .map { it.uid }
                    .toSet()
            }
            searchStatus.SearchPager(
                onSearchStatusChange = { rawSearchStatus = it },
                defaultResult = {
                    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                    if (recentlyInstalledResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical(),
                        ) {
                            item {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.recently_installed),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                            items(recentlyInstalledResults, key = { it.uid }, contentType = { "recent-group" }) { group ->
                                Column {
                                    GroupItem(
                                        group = group,
                                        onToggleExpand = {},
                                    ) {
                                        groupByUid[group.uid]?.let(onOpenProfile)
                                    }
                                    AnimatedVisibility(
                                        visible = group.apps.size > 1,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            group.apps.forEach { app ->
                                                SimpleAppItem(app = app)
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(maxOf(bottomInnerPadding, imeBottomPadding)))
                            }
                        }
                    }
                },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                ) {
                    item {
                        Spacer(Modifier.height(6.dp))
                    }
                    items(searchResults, key = { it.uid }, contentType = { "group" }) { group ->
                        val expanded = expandedSearchUids.value.contains(group.uid)
                        AnimatedVisibility(
                            visible = searchResults.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                GroupItem(
                                    group = group,
                                    onToggleExpand = {
                                        if (group.apps.size > 1) {
                                            expandedSearchUids.value =
                                                if (expanded) expandedSearchUids.value - group.uid else expandedSearchUids.value + group.uid
                                        }
                                    },
                                ) {
                                    groupByUid[group.uid]?.let(onOpenProfile)
                                }
                                AnimatedVisibility(
                                    visible = expanded && group.apps.size > 1,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        group.apps.forEach { app ->
                                            SimpleAppItem(
                                                app = app,
                                                matched = group.matchedPackageNames.contains(app.packageName),
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(maxOf(bottomInnerPadding, imeBottomPadding)))
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            val lazyListState = rememberLazyListState()
            val refreshTick = remember { mutableIntStateOf(0) }
            val latestGroupedApps = rememberUpdatedState(groupedApps)
            val latestRefreshing = rememberUpdatedState(isRefreshing)
            ScrollToTopOnChange(
                lazyListState,
                sortConfig,
                showSystemApps,
                showOnlyPrimaryUserApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }
            val pullToRefreshState = rememberPullToRefreshState()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )

            if (groupedApps.isEmpty() && !hasLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = bottomInnerPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                val expandedUids = remember { mutableStateOf(setOf<Int>()) }
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = {
                        onRefresh()
                        refreshTick.intValue++
                    },
                    refreshTexts = refreshTexts,
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 6.dp,
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection)
                    ),
                ) {
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding() + 6.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection)
                            ),
                            overscrollEffect = null,
                        ) {
                            items(groupedApps, key = { it.uid }, contentType = { "group" }) { group ->
                                val expanded = expandedUids.value.contains(group.uid)
                                Column {
                                    GroupItem(
                                        group = group,
                                        onToggleExpand = {
                                            if (group.apps.size > 1) {
                                                expandedUids.value =
                                                    if (expanded) expandedUids.value - group.uid else expandedUids.value + group.uid
                                            }
                                        }
                                    ) {
                                        groupByUid[group.uid]?.let(onOpenProfile)
                                    }
                                    AnimatedVisibility(
                                        visible = expanded && group.apps.size > 1,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            group.apps.forEach { app ->
                                                SimpleAppItem(app = app)
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(bottomInnerPadding))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: AppInfo,
    matched: Boolean = false,
) {
    Row {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .width(6.dp)
                .height(24.dp)
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(16.dp))
                .background(if (matched) colorScheme.primary else colorScheme.primaryContainer)
        )
        Card(
            modifier = Modifier
                .padding(start = 6.dp, end = 12.dp, bottom = 6.dp)
        ) {
            BasicComponent(
                title = app.label,
                summary = app.packageName,
                startAction = {
                    AppIconImage(
                        packageInfo = app.packageInfo,
                        label = app.label,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                    )
                },
                insideMargin = PaddingValues(horizontal = 9.dp)
            )
        }
    }
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    onToggleExpand: () -> Unit,
    onClickPrimary: () -> Unit,
) {
    val isInDarkTheme = isInDarkTheme()
    val bg = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val rootBg = colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val unmountBg = if (isInDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
    val fg = colorScheme.onSecondaryContainer
    val rootFg = colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    val unmountFg = if (isInDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.8f)

    val userId = group.uid / 100000
    val tags = remember(group.anyAllowSu, group.shouldUmount, group.anyCustom, userId) {
        buildList {
            if (group.anyAllowSu) add(StatusMeta("ROOT", rootBg, rootFg))
            if (group.shouldUmount) add(StatusMeta("UMOUNT", unmountBg, unmountFg))
            if (group.anyCustom) add(StatusMeta("CUSTOM", bg, fg))
            if (userId != 0) add(StatusMeta("USER $userId", bg, fg))
        }
    }
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onClick = onClickPrimary,
        onLongPress = if (group.apps.size > 1) onToggleExpand else null,
        showIndication = true,
        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(48.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                Text(
                    text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                    modifier = Modifier.basicMarquee(),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (group.apps.size > 1) {
                        stringResource(R.string.group_contains_apps, group.apps.size)
                    } else {
                        group.primary.packageName
                    },
                    modifier = Modifier
                        .basicMarquee(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (tags.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        StatusTag(
                            label = tag.label,
                            backgroundColor = tag.bg,
                            contentColor = tag.fg
                        )
                    }
                }
            }
            val layoutDirection = LocalLayoutDirection.current
            Image(
                modifier = Modifier
                    .graphicsLayer {
                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                    }
                    .padding(start = 8.dp)
                    .size(width = 10.dp, height = 16.dp),
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
            )
        }
    }
}

// Adapts ReSukiSU's AppGroup into the GroupedApps shape the Miuix superuser screen renders.
private fun AppGroup.toTiannGroupedApps(matched: Set<String> = emptySet()): GroupedApps =
    GroupedApps(
        uid = uid,
        apps = apps,
        primary = apps.first(),
        anyAllowSu = allowSu,
        anyCustom = hasCustomProfile,
        shouldUmount = profile?.umountModules == true,
        ownerName = userName,
        matchedPackageNames = matched,
    )
