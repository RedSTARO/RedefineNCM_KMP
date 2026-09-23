package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource

/**
 * The daily recommendation as a list: the home page's carousel shows covers and titles only, so
 * this is where the artist, album and length of each song can be read and a song picked.
 */
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
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    ExpressivePage(
        accentPalette = palette,
        contentWindowInsets = WindowInsets.statusBars,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "daily-header") {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    IconButton(onClick = onBack) {
                        Surface(
                            shape = CircleShape,
                            color = palette.quietContainer.copy(alpha = 0.72f),
                            contentColor = palette.onQuietContainer,
                        ) {
                            Icon(
                                AppIcons.ArrowBack,
                                contentDescription = strings.back,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = strings.dailyPicks,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.onPageStart,
                        )
                        Text(
                            text = if (songs.isEmpty()) strings.todaysPicks else strings.dailyPicksSongCount(songs.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.secondaryOnPageStart,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (queue.isNotEmpty()) {
                                        PlaybackSource.set(DailySource)
                                        player.setQueue(queue, 0)
                                    }
                                },
                                enabled = queue.isNotEmpty(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.onAccent,
                                ),
                            ) {
                                Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.playAll)
                            }
                        }
                    }
                }
            }
            when {
                loadError != null && songs.isEmpty() -> item(key = "daily-error") {
                    ExpressiveStatePanel(
                        title = strings.dailyPicksLoadFailed,
                        message = loadError?.text.orEmpty(),
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = palette,
                        actionLabel = strings.retry,
                        onAction = viewModel::retryAccountData,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                accountLoading && songs.isEmpty() -> item(key = "daily-loading") {
                    ExpressiveLoadingState(
                        label = strings.dailyPicksLoading,
                        accentColor = palette.accent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                songs.isEmpty() -> item(key = "daily-empty") {
                    ExpressiveStatePanel(
                        title = strings.dailyPicksEmpty,
                        message = strings.dailyPicksSignInHint,
                        icon = AppIcons.MusicNote,
                        accentPalette = palette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                ) { index, song ->
                    SongRow(
                        index = index,
                        title = song.name,
                        artist = song.ar.joinToString(" / ") { it.name },
                        artworkUri = song.al.picUrl,
                        shape = connectedListItemShape(index, songs.size),
                        onClick = {
                            playFromList(player, queue, index, playWholeList, source = DailySource)
                        },
                        songId = song.id,
                        accentColor = MaterialTheme.colorScheme.primaryContainer,
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
}
