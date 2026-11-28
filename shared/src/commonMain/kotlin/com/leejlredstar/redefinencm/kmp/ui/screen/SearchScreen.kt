package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.dto.SearchHotItem
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.MaxSavedEditorLength
import com.leejlredstar.redefinencm.kmp.ui.component.SearchEditorCorner
import com.leejlredstar.redefinencm.kmp.ui.component.boundedSavedEditorText
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberConnectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.component.rememberSaveableEditorText
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

/**
 * The search tab: one tap from anywhere, and it keeps its query while the user is away.
 *
 * @param focusRequest raised by the home page's search pill and Ctrl/⌘+F: put the cursor in the
 *   field and bring up the keyboard. Switching to the tab by itself does not.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    bottomPadding: Dp,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    isActive: Boolean = true,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
    searchEntryModifier: Modifier = Modifier,
) {
    val results by viewModel.searchResults.collectAsState()
    val entries by viewModel.searchEntries.collectAsState()
    val failedProviders by viewModel.searchFailedProviders.collectAsState()
    val groups by viewModel.searchGroups.collectAsState()
    val aggregationMode by viewModel.searchAggregationMode.collectAsState()
    val suggested by viewModel.searchSuggestions.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val submittedQuery by viewModel.searchSubmittedQuery.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val hasMore by viewModel.searchHasMore.collectAsState()
    val loadingMore by viewModel.searchLoadingMore.collectAsState()
    val moreError by viewModel.searchMoreError.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val hotSearches by viewModel.hotSearches.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editor = rememberEditorFocus(isActive, openRequest = focusRequest, onOpenRequestHandled = onFocusRequestHandled)
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val searchPrediction = remember { settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true) }

    val queryField = rememberSaveableEditorText()
    val query = queryField.text.toString()
    // Save the submitted query and list offsets, not network results and provider cursors.
    var lastSubmission by rememberSaveable { mutableStateOf<String?>(null) }
    val resultsState = rememberLazyListState()
    val suggestionsState = rememberLazyListState()
    val startState = rememberLazyListState()
    val panelState = rememberLazyListState()
    val suggestions = suggested?.takeIf { it.query == query.trim() }?.items.orEmpty()

    LaunchedEffect(isActive, viewModel) {
        if (!isActive) return@LaunchedEffect
        viewModel.loadHotSearches()
        lastSubmission?.let(viewModel::restoreSearch)
    }
    LaunchedEffect(isActive, query, submittedQuery, searchPrediction) {
        if (!isActive) return@LaunchedEffect
        when {
            query.isBlank() && lastSubmission != null -> {
                lastSubmission = null
                resultsState.requestScrollToItem(0)
                viewModel.clearSearch()
            }
            query.isNotBlank() && query.trim() != submittedQuery && searchPrediction -> {
                // A recreated screen may already have suggestions (and their scroll position).
                if (viewModel.requestSearchSuggestions(query)) suggestionsState.requestScrollToItem(0)
            }
            else -> viewModel.clearSearchSuggestions()
        }
    }

    fun submit(text: String) {
        val draft = boundedSavedEditorText(text)
        val term = draft.trim()
        if (term.isEmpty()) return
        if (term != lastSubmission) resultsState.requestScrollToItem(0)
        lastSubmission = term
        if (queryField.text.toString() != draft) queryField.setTextAndPlaceCursorAtEnd(draft)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        viewModel.search(term)
    }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val submittedMatchesQuery = submittedQuery != null && submittedQuery == query.trim()
    val perProvider = aggregationMode == LibraryAggregationMode.PER_PROVIDER && groups.size > 1
    val showingResults = submittedMatchesQuery && !loading && results.isNotEmpty() &&
        (if (perProvider) groups.isNotEmpty() else entries.isNotEmpty())
    val showingSuggestions = !submittedMatchesQuery && query.isNotBlank() && searchPrediction && suggestions.isNotEmpty()
    val showingStart = query.isBlank() && (history.isNotEmpty() || hotSearches.isNotEmpty())
    // Never measure a restored list anchor against the loading/error/prompt item's geometry.
    val bodyState = when {
        showingResults -> resultsState
        showingSuggestions -> suggestionsState
        showingStart -> startState
        else -> panelState
    }

    ExpressivePage(
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
        contentWindowInsets = WindowInsets.statusBars,
    ) {
        val gutter = LocalExpressivePageGutter.current
        val inset = Modifier.padding(horizontal = gutter)
        val scheme = MaterialTheme.colorScheme
        val searchHint = SearchPlaceholder
        val editorShape = RoundedCornerShape(SearchEditorCorner)
        Column(Modifier.fillMaxSize()) {
            // Pin the one editor to the pane's safe top/start. IME and dock changes leave it
            // here and resize the scrollable body instead; no heading can displace the field.
            TextField(
                state = queryField,
                placeholder = { Text(searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(MaxSavedEditorLength),
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                            IconButton(onClick = {
                                queryField.setTextAndPlaceCursorAtEnd("")
                                lastSubmission = null
                                resultsState.requestScrollToItem(0)
                                viewModel.clearSearch()
                            }) {
                                Icon(AppIcons.Clear, contentDescription = strings.clearSearch)
                            }
                            // A visible submit action for touch, mouse and keyboard users.
                            FilledTonalIconButton(
                                onClick = { submit(queryField.text.toString()) },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = scheme.surfaceContainerLowest,
                                    contentColor = scheme.onSurface,
                                ),
                            ) {
                                Icon(AppIcons.Search, contentDescription = strings.search)
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = editor.showKeyboardOnFocus),
                onKeyboardAction = { submit(queryField.text.toString()) },
                shape = editorShape,
                interactionSource = fieldInteraction,
                modifier = inset.padding(top = 8.dp, bottom = 4.dp).widthIn(max = SearchEntryMaxWidth).fillMaxWidth()
                    .then(searchEntryModifier)
                    .then(editor.modifier)
                    .border(2.dp, if (fieldFocused) scheme.outline else Color.Transparent, editorShape)
                    .semantics { contentDescription = searchHint },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                    focusedLeadingIconColor = scheme.onSurface,
                    unfocusedLeadingIconColor = scheme.onSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            LazyColumn(
                state = bodyState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
            ) {
                item(key = "page-heading") {
                    ExpressivePageHeading(
                        title = strings.search,
                        supportingText = strings.searchHeadingSubtitle,
                        modifier = inset.padding(bottom = 12.dp),
                    )
                }
                when {
                    loading && submittedMatchesQuery -> item(key = "loading") {
                        ExpressiveLoadingState(
                            label = strings.searchingFor(submittedQuery ?: query),
                            modifier = inset.padding(top = 12.dp),
                        )
                    }
                    searchError != null && results.isEmpty() && submittedMatchesQuery -> item(key = "error") {
                        ExpressiveStatePanel(
                            title = strings.searchFailed,
                            message = searchError?.text.orEmpty(),
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = { submit(submittedQuery ?: query) },
                            modifier = inset.padding(top = 12.dp),
                        )
                    }
                    showingResults -> {
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

                        item(key = "results-heading") {
                            SearchBodyHeading(strings.searchResultsTitle, inset)
                        }
                        searchError?.let { partialFailure ->
                            item(key = "partial-failure") {
                                SearchProviderNotice(
                                    label = partialFailure.text,
                                    // Retry only the providers that missed their page; existing
                                    // hits stay in place.
                                    onRetry = viewModel::retryFailedSearchProviders.takeIf {
                                        failedProviders.isNotEmpty() && !loadingMore
                                    },
                                    modifier = inset.padding(bottom = 8.dp),
                                )
                            }
                        }
                        if (perProvider) {
                            groups.forEach { group ->
                                item(key = "header-${group.provider.key}") {
                                    Text(
                                        text = group.provider.displayName,
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = scheme.onSurfaceVariant,
                                        modifier = inset.padding(top = 12.dp, bottom = 8.dp).semantics { heading() },
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
                            // Play the same-song merge as one row, with the other providers
                            // available individually in that row's menu.
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
                                onLoadMore = { if (isActive) viewModel.loadMoreSearchResults() },
                            )
                        }
                    }
                    submittedMatchesQuery && results.isEmpty() -> item(key = "no-results") {
                        ExpressiveStatePanel(
                            title = strings.noResultsFound,
                            message = strings.noSongsMatchQuery(submittedQuery ?: query),
                            icon = AppIcons.Search,
                            modifier = inset.padding(top = 12.dp),
                        )
                    }
                    showingSuggestions -> {
                        item(key = "suggestions-heading") {
                            SearchBodyHeading(strings.searchSuggestions, inset)
                        }
                        itemsIndexed(
                            items = suggestions,
                            key = { _, keyword -> keyword },
                        ) { index, keyword ->
                            val interaction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = { submit(keyword) },
                                shape = rememberConnectedListItemShape(index, suggestions.size, interaction),
                                color = scheme.surfaceContainerLow,
                                contentColor = scheme.onSurface,
                                interactionSource = interaction,
                                modifier = inset.fillMaxWidth().padding(bottom = ExpressiveLayout.ConnectedItemGap),
                            ) {
                                Row(
                                    modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(AppIcons.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                                    Text(keyword, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    !submittedMatchesQuery && query.isNotBlank() -> item(key = "search-prompt") {
                        ExpressiveStatePanel(
                            title = strings.readyToSearch,
                            message = strings.searchPromptWithQuery(query),
                            icon = AppIcons.Search,
                            modifier = inset,
                        )
                    }
                    query.isBlank() -> searchStart(history, hotSearches, inset, ::submit, viewModel::clearSearchHistory)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.searchStart(
    history: List<String>,
    hotSearches: List<SearchHotItem>,
    inset: Modifier,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty() && hotSearches.isEmpty()) {
        item(key = "search-empty") {
            ExpressiveStatePanel(
                title = strings.searchEmptyTitle,
                message = strings.searchEmptyHint,
                icon = AppIcons.Search,
                modifier = inset.padding(top = 12.dp),
            )
        }
        return
    }
    if (history.isNotEmpty()) {
        item(key = "history") {
            val scheme = MaterialTheme.colorScheme
            Surface(
                modifier = inset.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerLow,
                contentColor = scheme.onSurface,
            ) {
                Column(Modifier.padding(12.dp)) {
                    ExpressiveSectionTitle(
                        text = strings.searchHistory,
                        action = {
                            TextButton(
                                onClick = onClearHistory,
                                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant),
                            ) { Text(strings.clearSearchHistory) }
                        },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        history.forEach { word ->
                            key(word) {
                                SuggestionChip(
                                    onClick = { onPick(word) },
                                    label = { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = scheme.surfaceContainerHigh,
                                        labelColor = scheme.onSurface,
                                    ),
                                    border = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val hot = hotSearches.take(HotSearchCount)
    if (hot.isNotEmpty()) {
        item(key = "hot-title") {
            ExpressiveSectionTitle(
                text = strings.popularSearches,
                modifier = inset.padding(top = if (history.isEmpty()) 8.dp else 24.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(hot, key = { index, item -> "hot-$index-${item.searchWord}" }) { index, item ->
            PopularSearchRow(index, hot.size, item, { onPick(item.searchWord) }, inset)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PopularSearchRow(index: Int, count: Int, item: SearchHotItem, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val leadingRank = index < 3
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = rememberConnectedListItemShape(index, count, interaction),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().padding(bottom = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (leadingRank) scheme.surfaceContainerHighest else Color.Transparent,
                contentColor = if (leadingRank) scheme.onSurface else scheme.onSurfaceVariant,
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = if (leadingRank) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.searchWord, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.content.isNotBlank()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBodyHeading(title: String, modifier: Modifier = Modifier) {
    ExpressiveSectionTitle(
        text = title,
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SearchProviderNotice(label: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.onErrorContainer),
                ) { Text(strings.retry) }
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
    onLoadMore: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        val style = MaterialTheme.typography.bodyMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            loadingMore -> Text(strings.loadingMoreResults, style = style, color = color)
            moreError != null -> TextButton(onClick = onLoadMore) { Text(strings.errorTapToRetry(moreError)) }
            hasMore -> {
                // Reaching the end loads the next page; the button is there if that stalls.
                LaunchedEffect(shownCount) { onLoadMore() }
                TextButton(onClick = onLoadMore) { Text(strings.loadMore) }
            }
            else -> Text(strings.noMoreResults, style = style, color = color)
        }
    }
}

/** Downloads take a numeric NetEase song ID; a foreign track must never show a wrong status. */
@Composable
private fun SearchTrackRow(
    index: Int,
    track: ProviderTrack,
    count: Int,
    showProviderBadge: Boolean,
    onClick: () -> Unit,
    alsoFrom: List<ProviderTrack> = emptyList(),
    onPlayAlternate: (ProviderTrack) -> Unit = {},
) {
    val media = remember(track) { track.toMediaInfo() }
    val rowActions = rememberSongRowActions(media = media, neteaseSong = remember(track) { track.toNeteaseSongOrNull() })
    val playAlternate by rememberUpdatedState(onPlayAlternate)
    SongRow(
        index = index,
        title = track.title,
        artist = track.artistLine,
        artworkUri = track.artworkUrl,
        shape = connectedListItemShape(index, count),
        onClick = onClick,
        songId = track.id.neteaseIdOrNull,
        mediaId = media.id,
        durationMs = track.durationMillis,
        album = track.album?.name.orEmpty(),
        badge = track.provider.displayName.takeIf { showProviderBadge },
        badges = alsoFrom.map { it.provider.displayName }.takeIf { showProviderBadge }.orEmpty() + track.tags.map { it.label },
        actions = remember(rowActions, alsoFrom, I18n.language) {
            alsoFrom.map { alternate ->
                SongRowAction(strings.playFromProviderInstead(alternate.provider.displayName), AppIcons.PlayArrow) { playAlternate(alternate) }
            } + rowActions
        },
    )
}

/** The same words wherever search is offered: it finds songs, matched by title, artist or album. */
internal val SearchPlaceholder: String get() = strings.searchSongs

/** A NetEase search hit in the DTO shape the downloader takes; null for other providers. */
internal fun ProviderTrack.toNeteaseSongOrNull(): SongDetailSongs? {
    val songId = id.neteaseIdOrNull ?: return null
    return SongDetailSongs(
        id = songId,
        name = title,
        ar = artists.map { SongArtist(id = it.id?.neteaseIdOrNull ?: 0L, name = it.name) },
        al = SongAlbum(id = album?.id?.neteaseIdOrNull ?: 0L, name = album?.name.orEmpty(), picUrl = artworkUrl),
        dt = durationMillis,
    )
}

private const val HotSearchCount = 20
