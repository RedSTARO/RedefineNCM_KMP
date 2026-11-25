package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

/** The daily recommendation, with the artist, album and length of each song available in its list. */
@Composable
fun DailySongsScreen(
    onBack: () -> Unit,
    scaffoldPadding: PaddingValues = PaddingValues(),
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val loadError by viewModel.recommendSongsLoadError.collectAsState()
    val songs = recommend?.data?.dailySongs.orEmpty()
    val queue = remember(songs) { songs.map { it.toMediaInfo() } }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }

    CatalogPage(title = strings.dailyPicks, onBack = onBack, scaffoldPadding = scaffoldPadding) {
        if (songs.isNotEmpty()) {
            item(key = "daily-header") {
                CatalogFeatureHeader(
                    title = strings.dailyPicks,
                    supportingText = strings.dailyPicksSongCount(songs.size),
                    icon = AppIcons.GraphicEq,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 12.dp),
                    actions = {
                        CatalogPrimaryButton(
                            label = strings.playAll,
                            icon = AppIcons.PlayArrow,
                            onClick = {
                                PlaybackSource.set(DailySource)
                                player.setQueue(queue, 0)
                            },
                        )
                    },
                )
            }
        }
        when {
            loadError != null && songs.isEmpty() -> item(key = "daily-error") {
                ExpressiveStatePanel(
                    title = strings.dailyPicksLoadFailed,
                    message = loadError?.text.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = viewModel::retryAccountData,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            accountLoading && songs.isEmpty() -> item(key = "daily-loading") {
                ExpressiveLoadingState(
                    label = strings.dailyPicksLoading,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            songs.isEmpty() -> item(key = "daily-empty") {
                ExpressiveStatePanel(
                    title = strings.dailyPicksEmpty,
                    message = strings.dailyPicksSignInHint,
                    icon = AppIcons.MusicNote,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            else -> itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    index = index,
                    title = song.name,
                    artist = song.ar.joinToString(" / ") { it.name },
                    artworkUri = song.al.picUrl,
                    shape = connectedListItemShape(index, songs.size),
                    onClick = { playFromList(player, queue, index, playWholeList, source = DailySource) },
                    songId = song.id,
                    durationMs = song.dt,
                    album = song.al.name,
                    badge = songFeeBadge(song.fee),
                    actions = rememberSongRowActions(
                        media = queue.getOrNull(index) ?: song.toMediaInfo(),
                        neteaseSong = song,
                    ),
                )
            }
        }
    }
}
