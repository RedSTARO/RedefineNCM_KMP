package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberConnectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.pageLinkColor
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.data.api.dto.SearchHotItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement

/**
 * The search tab. It used to be an overlay inside the recommendations page, reachable from
 * there only; as a tab it is one tap from anywhere and keeps its query while the user is away.
 *
 * @param focusRequest raised by the home page's search pill and Ctrl/⌘+F: put the cursor in the
 *   field and bring up the keyboard. Switching to the tab by itself does not.
 */
@Composable
fun SearchScreen(
    bottomPadding: androidx.compose.ui.unit.Dp,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val results by viewModel.searchResults.collectAsState()
    val entries by viewModel.searchEntries.collectAsState()
    val failedProviders by viewModel.searchFailedProviders.collectAsState()
    val groups by viewModel.searchGroups.collectAsState()
    val aggregationMode by viewModel.searchAggregationMode.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val submittedQuery by viewModel.searchSubmittedQuery.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val hasMore by viewModel.searchHasMore.collectAsState()
    val loadingMore by viewModel.searchLoadingMore.collectAsState()
    val moreError by viewModel.searchMoreError.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val hotSearches by viewModel.hotSearches.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val searchPrediction = remember { settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true) }
    val searchPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadHotSearches() }
    val onQueryChange: (String) -> Unit = { query = it }

    LaunchedEffect(focusRequest) {
        if (focusRequest <= 0) return@LaunchedEffect
        delay(220)
        if (runCatching { focusRequester.requestFocus() }.isSuccess) {
            keyboard?.show()
        }
        onFocusRequestHandled()
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            viewModel.clearSearch()
        } else if (searchPrediction) {
            delay(300)
            viewModel.fetchSearchSuggestions(query)
        }
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        onQueryChange(text)
        keyboard?.hide()
        viewModel.search(text)
    }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val submittedMatchesQuery = submittedQuery != null && submittedQuery == query.trim()

    ExpressivePage(
        accentPalette = searchPalette,
        maxContentWidth = com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout.ReadingContentMaxWidth,
        contentWindowInsets = WindowInsets.statusBars,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = searchPalette.onPageStart,
            modifier = Modifier.padding(
                start = SearchSpacing.Inset,
                top = SearchSpacing.SectionGap,
                bottom = SearchSpacing.TitleGap,
            ),
        )
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(SearchPlaceholder) },
                singleLine = true,
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Row {
                            IconButton(onClick = { onQueryChange(""); viewModel.clearSearch() }) {
                                Icon(AppIcons.Clear, contentDescription = "清除")
                            }
                            // The keyboard's search key was the only way to run a search.
                            IconButton(onClick = { submit(query) }) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = "搜索",
                                    tint = searchPalette.accent,
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                shape = CircleShape,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = searchPalette.quietContainer,
                    unfocusedContainerColor = searchPalette.quietContainer,
                    focusedTextColor = searchPalette.onQuietContainer,
                    unfocusedTextColor = searchPalette.onQuietContainer,
                    focusedLeadingIconColor = searchPalette.accent,
                    unfocusedLeadingIconColor = searchPalette.secondaryOnQuietContainer,
                    focusedTrailingIconColor = searchPalette.onQuietContainer,
                    unfocusedTrailingIconColor = searchPalette.onQuietContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading && submittedMatchesQuery -> {
                ExpressiveLoadingState(
                    label = "正在搜索“${submittedQuery ?: query}”…",
                    accentColor = searchPalette.accent,
                    modifier = Modifier.padding(top = SearchSpacing.SectionGap),
                )
            }
            // Only a total failure replaces the results. With two providers configured, one being
            // down still leaves the other's hits worth showing — that case is reported by the
            // notice inside the list instead.
            searchError != null && results.isEmpty() && submittedMatchesQuery -> {
                ExpressiveStatePanel(
                    title = "搜索失败",
                    message = searchError.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = searchPalette,
                    actionLabel = "重试",
                    onAction = { submit(submittedQuery ?: query) },
                    modifier = Modifier.padding(top = SearchSpacing.SectionGap),
                )
            }
            results.isNotEmpty() && submittedMatchesQuery -> {
                // Both aggregation views ship. Merged interleaves providers into one list; the
                // per-provider view keeps them under their own headers. The setting picks.
                val perProvider = aggregationMode == LibraryAggregationMode.PER_PROVIDER &&
                    groups.size > 1
                val showProviderBadge = groups.size > 1 && !perProvider

                // Playing keeps the results on screen: the next result is one tap away.
                fun play(list: List<ProviderTrack>, index: Int) {
                    playFromList(
                        player,
                        list.map { it.toMediaInfo() },
                        index,
                        playWholeList,
                        source = "搜索「${submittedQuery ?: query}」",
                    )
                    player.play()
                }

                // Bottom clearance so the last results can be scrolled clear of the floating
                // navigation and the mini player.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    searchError?.let { partialFailure ->
                        item(key = "partial-failure") {
                            SearchProviderHeader(
                                label = partialFailure,
                                accent = MaterialTheme.colorScheme.error,
                                // The failed providers are asked for the page they missed; the
                                // results already shown stay where they are.
                                actionLabel = if (failedProviders.isNotEmpty() && !loadingMore) "重试" else null,
                                onAction = viewModel::retryFailedSearchProviders,
                            )
                        }
                    }
                    if (perProvider) {
                        groups.forEach { group ->
                            item(key = "header-${group.provider.key}") {
                                SearchProviderHeader(
                                    label = group.provider.displayName,
                                    accent = searchPalette.accent,
                                )
                            }
                            itemsIndexed(
                                items = group.tracks,
                                key = { _, track -> "${group.provider.key}-${track.id}" },
                            ) { index, track ->
                                SearchTrackRow(
                                    index = index,
                                    track = track,
                                    count = group.tracks.size,
                                    showProviderBadge = false,
                                    onClick = { play(group.tracks, index) },
                                )
                            }
                        }
                    } else {
                        // One row per song: the same song from several providers is folded into
                        // one row that names them all, and the others can be played from its menu.
                        val played = entries.map { it.track }
                        itemsIndexed(
                            items = entries,
                            key = { _, entry -> entry.track.id.toString() },
                        ) { index, entry ->
                            SearchTrackRow(
                                index = index,
                                track = entry.track,
                                count = entries.size,
                                showProviderBadge = showProviderBadge,
                                alsoFrom = entry.alternates,
                                onClick = { play(played, index) },
                                onPlayAlternate = { alternate ->
                                    play(played.toMutableList().also { it[index] = alternate }, index)
                                },
                            )
                        }
                    }
                    item(key = "results-footer") {
                        SearchResultsFooter(
                            hasMore = hasMore,
                            loadingMore = loadingMore,
                            moreError = moreError,
                            shownCount = results.size,
                            accent = searchPalette.secondaryOnQuietContainer,
                            linkColor = searchPalette.pageLinkColor(),
                            onLoadMore = viewModel::loadMoreSearchResults,
                        )
                    }
                }
            }
            submittedMatchesQuery -> {
                ExpressiveStatePanel(
                    title = "没有找到结果",
                    message = "没有找到与“$submittedQuery”匹配的歌曲，试试更短的关键词或只输入歌手名。",
                    icon = AppIcons.Search,
                    accentPalette = searchPalette,
                    modifier = Modifier.padding(top = SearchSpacing.SectionGap),
                )
            }
            query.isNotBlank() && searchPrediction && suggestions.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    itemsIndexed(
                        items = suggestions,
                        key = { _, keyword -> keyword },
                    ) { index, keyword ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { submit(keyword) },
                            shape = rememberConnectedListItemShape(
                                index = index,
                                count = suggestions.size,
                                interactionSource = interactionSource,
                            ),
                            color = searchPalette.quietContainer,
                            contentColor = searchPalette.onQuietContainer,
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ExpressiveLayout.ConnectedItemGap),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = null,
                                    tint = searchPalette.accent,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(text = keyword, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            query.isNotBlank() -> {
                ExpressiveStatePanel(
                    title = "准备搜索",
                    message = "点搜索按钮或按回车键，查找与“$query”相关的歌曲。",
                    icon = AppIcons.Search,
                    accentPalette = searchPalette,
                )
            }
            else -> SearchStart(
                history = history,
                hotSearches = hotSearches,
                accentPalette = searchPalette,
                bottomPadding = bottomPadding,
                onPick = ::submit,
                onClearHistory = viewModel::clearSearchHistory,
            )
        }
        }
    }
}

/**
 * What search offers before a query: the user's recent searches and today's hot-search chart.
 * It used to be one line of instructions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchStart(
    history: List<String>,
    hotSearches: List<SearchHotItem>,
    accentPalette: ContentAccentPalette,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty() && hotSearches.isEmpty()) {
        ExpressiveStatePanel(
            title = "发现想听的音乐",
            message = "输入歌名、歌手或专辑名，查找相关的歌曲。",
            icon = AppIcons.Search,
            accentPalette = accentPalette,
            modifier = Modifier.padding(top = SearchSpacing.SectionGap),
        )
        return
    }
    val hot = hotSearches.take(HotSearchCount)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        if (history.isNotEmpty()) {
            item(key = "history-title") {
                ExpressiveSectionTitle(
                    text = "搜索历史",
                    action = {
                        TextButton(
                            onClick = onClearHistory,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = accentPalette.pageLinkColor(),
                            ),
                        ) { Text("清除") }
                    },
                    modifier = Modifier.padding(
                        start = SearchSpacing.Inset,
                        top = SearchSpacing.SectionGap,
                        bottom = SearchSpacing.TitleGap,
                    ),
                )
            }
            item(key = "history") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SearchSpacing.ChipGap),
                    verticalArrangement = Arrangement.spacedBy(SearchSpacing.ChipGap),
                    modifier = Modifier.padding(start = SearchSpacing.Inset),
                ) {
                    history.forEach { word ->
                        SuggestionChip(
                            onClick = { onPick(word) },
                            label = { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = accentPalette.quietContainer,
                                labelColor = accentPalette.onQuietContainer,
                            ),
                            border = null,
                        )
                    }
                }
            }
        }
        if (hot.isNotEmpty()) {
            item(key = "hot-title") {
                ExpressiveSectionTitle(
                    text = "热门搜索",
                    modifier = Modifier.padding(
                        start = SearchSpacing.Inset,
                        top = SearchSpacing.SectionGap,
                        bottom = SearchSpacing.TitleGap,
                    ),
                )
            }
            itemsIndexed(
                items = hot,
                key = { index, item -> "hot-$index-${item.searchWord}" },
            ) { index, item ->
                Surface(
                    onClick = { onPick(item.searchWord) },
                    shape = connectedListItemShape(index, hot.size),
                    color = accentPalette.quietContainer,
                    contentColor = accentPalette.onQuietContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ExpressiveLayout.ConnectedItemGap),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (index < 3) accentPalette.accent else accentPalette.secondaryOnQuietContainer,
                            modifier = Modifier.width(32.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.searchWord,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.content.isNotBlank()) {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentPalette.secondaryOnQuietContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The end of the results: the next page loading, a retry, or the word that there is no more. */
@Composable
private fun SearchResultsFooter(
    hasMore: Boolean,
    loadingMore: Boolean,
    moreError: String?,
    shownCount: Int,
    accent: Color,
    linkColor: Color,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadingMore -> Text(
                text = "正在加载更多结果…",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
            moreError != null -> TextButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.textButtonColors(contentColor = linkColor),
            ) { Text("$moreError，点按重试") }
            hasMore -> {
                // Reaching the end loads the next page; the button is there if that stalls.
                LaunchedEffect(shownCount) { onLoadMore() }
                TextButton(
                    onClick = onLoadMore,
                    colors = ButtonDefaults.textButtonColors(contentColor = linkColor),
                ) { Text("加载更多") }
            }
            else -> Text(
                text = "没有更多结果了",
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }
    }
}

/** Section label for the per-provider view, and for the partial-failure notice. */
@Composable
private fun SearchProviderHeader(
    label: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            start = SearchSpacing.Inset,
            top = SearchSpacing.SectionGap,
            bottom = SearchSpacing.TitleGap,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.weight(1f, fill = false),
        )
        actionLabel?.let { action ->
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * One search hit.
 *
 * [SongRow]'s `songId` drives the download-status chip, which is NetEase-only: downloads are keyed
 * by a numeric song id and no other provider has one, so a foreign track passes null and shows no
 * chip rather than showing a wrong one.
 */
@Composable
private fun SearchTrackRow(
    index: Int,
    track: ProviderTrack,
    count: Int,
    showProviderBadge: Boolean,
    onClick: () -> Unit,
    /** The same song from other providers, folded into this row. */
    alsoFrom: List<ProviderTrack> = emptyList(),
    onPlayAlternate: (ProviderTrack) -> Unit = {},
) {
    val media = remember(track) { track.toMediaInfo() }
    val rowActions = rememberSongRowActions(
        media = media,
        neteaseSong = remember(track) { track.toNeteaseSongOrNull() },
    )
    // One accent for the whole result list. Tinting each row from its own cover striped the list
    // in unrelated colours. The provider, when several are mixed, is a chip in the row rather than
    // a line of its own above it.
    SongRow(
        index = index,
        title = track.title,
        artist = track.artistLine,
        artworkUri = track.artworkUrl,
        shape = connectedListItemShape(index, count),
        onClick = onClick,
        songId = track.id.neteaseIdOrNull,
        accentColor = MaterialTheme.colorScheme.primaryContainer,
        mediaId = media.id,
        durationMs = track.durationMillis,
        album = track.album?.name.orEmpty(),
        badge = track.provider.displayName.takeIf { showProviderBadge },
        // A folded row names every provider it stands for, then what the played one says of itself.
        badges = alsoFrom.map { it.provider.displayName }.takeIf { showProviderBadge }.orEmpty() +
            track.tags.map { it.label },
        actions = remember(rowActions, alsoFrom) {
            alsoFrom.map { alternate ->
                SongRowAction("改用${alternate.provider.displayName}播放", AppIcons.PlayArrow) {
                    onPlayAlternate(alternate)
                }
            } + rowActions
        },
    )
}

/** The same words wherever search is offered: it finds songs, matched by title, artist or album. */
internal const val SearchPlaceholder = "搜索歌曲"

/** A NetEase search hit in the DTO shape the downloader takes; null for other providers. */
internal fun ProviderTrack.toNeteaseSongOrNull(): com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs? {
    val songId = id.neteaseIdOrNull ?: return null
    return com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs(
        id = songId,
        name = title,
        ar = artists.map {
            com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist(
                id = it.id?.neteaseIdOrNull ?: 0L,
                name = it.name,
            )
        },
        al = com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum(
            id = album?.id?.neteaseIdOrNull ?: 0L,
            name = album?.name.orEmpty(),
            picUrl = artworkUrl,
        ),
        dt = durationMillis,
    )
}

/**
 * One spacing scale for the page's sections.
 *
 * There were five: section titles 4dp inside the gutter with tops of 8, 16 and 24dp and bottoms
 * of 0, 8 and 12dp; the history chips flush to the gutter while their own title was not; the hot
 * list padded by a third value again. Nothing about a search result is special enough to earn
 * its own rhythm, and side by side the page read as drifting rather than as sections.
 */
private object SearchSpacing {
    /** Anything that is not a full-width row lines up with the page title, 4dp in. */
    val Inset = 4.dp

    /** Above a section title, and above a state panel that stands in for one. */
    val SectionGap = 24.dp

    /** Between a section title and the content under it. */
    val TitleGap = 8.dp

    /** Between chips, on both axes. */
    val ChipGap = 8.dp
}

private const val HotSearchCount = 20
