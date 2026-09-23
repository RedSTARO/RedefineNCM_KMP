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
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
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
 * The search tab: one tap from anywhere, and it keeps its query while the user is away.
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
            text = strings.search,
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
                                Icon(AppIcons.Clear, contentDescription = strings.clearSearch)
                            }
                            // The keyboard's search key is not the only way to run a search.
                            IconButton(onClick = { submit(query) }) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = strings.search,
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
                    label = strings.searchingFor(submittedQuery ?: query),
                    accentColor = searchPalette.accent,
                    modifier = Modifier.padding(top = SearchSpacing.SectionGap),
                )
            }
            // Only a total failure replaces the results. With two providers configured, one being
            // down still leaves the other's hits worth showing; the notice inside the list reports
            // that case instead.
            searchError != null && results.isEmpty() && submittedMatchesQuery -> {
                ExpressiveStatePanel(
                    title = strings.searchFailed,
                    message = searchError?.text.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = searchPalette,
                    actionLabel = strings.retry,
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
                        source = (submittedQuery ?: query).let { searched -> UiText { it.playbackSourceSearch(searched) } },
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
                                label = partialFailure.text,
                                accent = MaterialTheme.colorScheme.error,
                                // The failed providers are asked for the page they missed; the
                                // results already shown stay where they are.
                                actionLabel = if (failedProviders.isNotEmpty() && !loadingMore) strings.retry else null,
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
                            moreError = moreError?.text,
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
                    title = strings.noResultsFound,
                    message = strings.noSongsMatchQuery(submittedQuery),
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
                    title = strings.readyToSearch,
                    message = strings.searchPromptWithQuery(query),
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
            title = strings.searchEmptyTitle,
            message = strings.searchEmptyHint,
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
                    text = strings.searchHistory,
                    action = {
                        TextButton(
                            onClick = onClearHistory,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = accentPalette.pageLinkColor(),
                            ),
                        ) { Text(strings.clearSearch) }
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
                    text = strings.popularSearches,
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
                text = strings.loadingMoreResults,
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
            moreError != null -> TextButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.textButtonColors(contentColor = linkColor),
            ) { Text(strings.errorTapToRetry(moreError)) }
            hasMore -> {
                // Reaching the end loads the next page; the button is there if that stalls.
                LaunchedEffect(shownCount) { onLoadMore() }
                TextButton(
                    onClick = onLoadMore,
                    colors = ButtonDefaults.textButtonColors(contentColor = linkColor),
                ) { Text(strings.loadMore) }
            }
            else -> Text(
                text = strings.noMoreResults,
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
    // One accent for the whole result list. Tinting each row from its own cover would stripe the
    // list in unrelated colours. The provider, when several are mixed, is a chip in the row rather
    // than a line of its own above it.
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
        actions = remember(rowActions, alsoFrom, I18n.language) {
            alsoFrom.map { alternate ->
                SongRowAction(strings.playFromProviderInstead(alternate.provider.displayName), AppIcons.PlayArrow) {
                    onPlayAlternate(alternate)
                }
            } + rowActions
        },
    )
}

/** The same words wherever search is offered: it finds songs, matched by title, artist or album. */
internal val SearchPlaceholder: String get() = strings.searchSongs

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
 * Nothing about a search result is special enough to earn its own rhythm. With separate values
 * per section, the page reads as drifting rather than as a set of sections.
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
