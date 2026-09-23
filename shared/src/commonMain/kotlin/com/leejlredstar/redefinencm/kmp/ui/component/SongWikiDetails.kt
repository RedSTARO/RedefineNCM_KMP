/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.SongWikiSection
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.legibleAccentFor
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState

/**
 * The song's details: what the service knows about the recording beyond its title.
 *
 * It opens from Now Playing, which follows the theme and is tinted from the cover, so it is a
 * bottom sheet in the app's own colours, like the queue and the comments beside it.
 *
 * @param artworkOverlay drawn over the cover: the dynamic cover video, where the song has one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongWikiDetailsSheet(
    visible: Boolean,
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    artworkUri: String?,
    durationMs: Long?,
    state: SongWikiUiState,
    accentPalette: ContentAccentPalette,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    fallbackArtworkUri: String? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = accentPalette.pageEnd,
        contentColor = accentPalette.onQuietContainer,
    ) {
        SongWikiDetailsContent(
            songTitle = songTitle,
            songArtist = songArtist,
            albumTitle = albumTitle,
            artworkUri = artworkUri,
            fallbackArtworkUri = fallbackArtworkUri,
            durationMs = durationMs,
            state = state,
            accentPalette = accentPalette,
            onRetry = onRetry,
            artworkOverlay = artworkOverlay,
        )
    }
}

@Composable
private fun SongWikiDetailsContent(
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    artworkUri: String?,
    fallbackArtworkUri: String?,
    durationMs: Long?,
    state: SongWikiUiState,
    accentPalette: ContentAccentPalette,
    onRetry: () -> Unit,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
) {
    val sections = (state as? SongWikiUiState.Content)?.summary?.sections.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "wiki-header") {
            SongWikiHeader(
                songTitle = songTitle,
                songArtist = songArtist,
                albumTitle = albumTitle,
                artworkUri = artworkUri,
                fallbackArtworkUri = fallbackArtworkUri,
                durationMs = durationMs,
                accentPalette = accentPalette,
                artworkOverlay = artworkOverlay,
            )
        }
        when {
            state is SongWikiUiState.Loading -> item(key = "wiki-loading") {
                ExpressiveLoadingState(
                    label = strings.loadingSongDetails,
                    accentColor = accentPalette.accent,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            state is SongWikiUiState.Error -> item(key = "wiki-error") {
                ExpressiveStatePanel(
                    title = strings.songDetailsLoadFailed,
                    message = state.message.text,
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = accentPalette,
                    actionLabel = strings.retry,
                    onAction = onRetry,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            // The header above is the song's own metadata and still shows; only the wiki is
            // NetEase's.
            state is SongWikiUiState.Unsupported -> item(key = "wiki-unsupported") {
                ExpressiveStatePanel(
                    title = strings.songDetailsUnsupported,
                    message = strings.songDetailsUnsupportedMessage,
                    icon = AppIcons.Info,
                    accentPalette = accentPalette,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            sections.isEmpty() -> item(key = "wiki-empty") {
                ExpressiveStatePanel(
                    title = strings.noMoreSongDetails,
                    message = strings.noSongDetailsToShow,
                    icon = AppIcons.Info,
                    accentPalette = accentPalette,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            else -> itemsIndexed(
                items = sections,
                key = { index, section -> "wiki-$index-${section.title}" },
            ) { index, section ->
                SongWikiSectionRow(
                    section = section,
                    index = index,
                    count = sections.size,
                    accentPalette = accentPalette,
                )
            }
        }
    }
}

@Composable
private fun SongWikiHeader(
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    durationMs: Long?,
    artworkUri: String?,
    fallbackArtworkUri: String?,
    accentPalette: ContentAccentPalette,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.large),
        ) {
            // A downloaded song's cover is a local file that can fail to resolve; the
            // remote one is what it falls back to, as it does behind the lyrics.
            var shownArtwork by remember(artworkUri, fallbackArtworkUri) {
                mutableStateOf(artworkUri)
            }
            AsyncImage(
                model = shownArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (fallbackArtworkUri != null && shownArtwork != fallbackArtworkUri) {
                        shownArtwork = fallbackArtworkUri
                    }
                },
            )
            artworkOverlay?.invoke(this)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = songTitle?.takeIf { it.isNotBlank() } ?: strings.notPlaying,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onQuietContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            songArtist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val footnote = listOfNotNull(
                albumTitle?.takeIf { it.isNotBlank() },
                durationMs?.takeIf { it > 0L }?.let(::formatPlaybackDuration),
            ).joinToString(" · ")
            if (footnote.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One section: its name, the short values under it, and the paragraph that some of them carry.
 *
 * The values are short and there are usually several (credits, a tag, a chart position), so
 * they read as chips rather than as a column of one-word lines.
 */
@Composable
private fun SongWikiSectionRow(
    section: SongWikiSection,
    index: Int,
    count: Int,
    accentPalette: ContentAccentPalette,
) {
    Surface(
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // The accent's tone is fixed per scheme while the row's follows the artwork,
                // so on a pale cover in the light theme the two can land within 4:1.
                color = legibleAccentFor(
                    accent = accentPalette.accent,
                    background = accentPalette.quietContainer,
                    backdrop = MaterialTheme.colorScheme.surface,
                ),
            )
            if (section.values.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section.values.forEach { value ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = accentPalette.container,
                            contentColor = accentPalette.onContainer,
                        ) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            section.description?.let { description ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
        }
    }
}

/** The wiki state for [mediaId], or idle when the state belongs to another song. */
internal fun SongWikiUiState.scopedToMedia(mediaId: String?): SongWikiUiState {
    if (mediaId == null) return SongWikiUiState.Idle
    val stateMediaId = when (this) {
        is SongWikiUiState.Idle -> null
        is SongWikiUiState.Loading -> this.mediaId
        is SongWikiUiState.Content -> this.mediaId
        is SongWikiUiState.Empty -> this.mediaId
        is SongWikiUiState.Error -> this.mediaId
        is SongWikiUiState.Unsupported -> this.mediaId
    }
    return if (stateMediaId == mediaId) this else SongWikiUiState.Idle
}

/**
 * Whether opening the details should fetch them.
 *
 * Only from idle: closing and reopening an error keeps the error on screen, and its own retry
 * button is the way to try again for the same track.
 */
internal fun shouldRequestSongWikiOnOpen(
    state: SongWikiUiState,
    mediaId: String?,
): Boolean = mediaId != null && state.scopedToMedia(mediaId) is SongWikiUiState.Idle
