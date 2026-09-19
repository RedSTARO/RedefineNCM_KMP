package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    bottomPadding: androidx.compose.ui.unit.Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val results by viewModel.searchResults.collectAsState()
    val groups by viewModel.searchGroups.collectAsState()
    val aggregationMode by viewModel.searchAggregationMode.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val submittedQuery by viewModel.searchSubmittedQuery.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val searchPrediction = remember { settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true) }
    val searchPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    // The last query and its results are kept when the search closes, so trying another result
    // after playing one does not mean typing the query again.
    LaunchedEffect(Unit) {
        delay(220)
        if (runCatching { focusRequester.requestFocus() }.isSuccess) {
            keyboard?.show()
        }
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
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Surface(
                    shape = CircleShape,
                    color = searchPalette.quietContainer,
                    contentColor = searchPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            with(sharedTransitionScope) {
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
                        .sharedBounds(
                            rememberSharedContentState(SharedKeys.search()),
                            animatedVisibilityScope,
                        )
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
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading && submittedMatchesQuery -> {
                ExpressiveLoadingState(
                    label = "正在搜索“${submittedQuery ?: query}”…",
                    accentColor = searchPalette.accent,
                    modifier = Modifier.padding(top = 24.dp),
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
                    modifier = Modifier.padding(top = 24.dp),
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
                        itemsIndexed(
                            items = results,
                            key = { _, track -> track.id.toString() },
                        ) { index, track ->
                            SearchTrackRow(
                                index = index,
                                track = track,
                                count = results.size,
                                showProviderBadge = showProviderBadge,
                                onClick = { play(results, index) },
                            )
                        }
                    }
                }
            }
            submittedMatchesQuery -> {
                ExpressiveStatePanel(
                    title = "没有找到结果",
                    message = "没有找到与“$submittedQuery”匹配的歌曲，试试更短的关键词或只输入歌手名。",
                    icon = AppIcons.Search,
                    accentPalette = searchPalette,
                    modifier = Modifier.padding(top = 24.dp),
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
            else -> {
                ExpressiveStatePanel(
                    title = "发现想听的音乐",
                    message = "输入歌名、歌手或专辑名，查找相关的歌曲。",
                    icon = AppIcons.Search,
                    accentPalette = searchPalette,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        }
    }
}

/** Section label for the per-provider view, and for the partial-failure notice. */
@Composable
private fun SearchProviderHeader(label: String, accent: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
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
) {
    val media = remember(track) { track.toMediaInfo() }
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
        actions = rememberSongRowActions(
            media = media,
            neteaseSong = remember(track) { track.toNeteaseSongOrNull() },
        ),
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
