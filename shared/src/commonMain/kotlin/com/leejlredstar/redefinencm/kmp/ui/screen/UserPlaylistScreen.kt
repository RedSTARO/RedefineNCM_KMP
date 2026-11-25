package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserDetailProfile
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.legibleAccentFor
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

internal data class UserLevelDisplay(
    val summary: String,
    val nextLevelLabel: String?,
    val progressLabel: String,
    val progress: Float,
)

internal fun isFavoritePlaylist(
    specialType: Int,
    name: String,
    creatorUserId: Long,
    currentUserId: Long,
): Boolean = currentUserId > 0L &&
    creatorUserId == currentUserId &&
    (specialType == 5 || (specialType == 0 && name.endsWith("喜欢的音乐")))

internal fun userLevelDisplay(response: UserLevelResponse?): UserLevelDisplay? {
    val data = response?.data ?: return null
    val progress = when {
        response.full -> 1f
        data.progress.isFinite() -> data.progress.coerceIn(0.0, 1.0).toFloat()
        else -> 0f
    }
    return UserLevelDisplay(
        summary = strings.userLevelSummary(data.level, data.nowPlayCount, data.nowLoginCount),
        nextLevelLabel = if (response.full) {
            null
        } else {
            buildList {
                if (data.nextPlayCount > 0) add(strings.userLevelNextSongs(data.nextPlayCount))
                if (data.nextLoginCount > 0) add(strings.userLevelNextDays(data.nextLoginCount))
            }.takeIf { it.isNotEmpty() }?.joinToString(
                separator = " · ",
                prefix = strings.userLevelNextPrefix,
            )
        },
        progressLabel = if (response.full) {
            strings.userLevelMax
        } else {
            strings.userLevelProgress((progress * 100).roundToInt())
        },
        progress = progress,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserPlaylistScreen(
    scaffoldPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenLocalLibrary: () -> Unit = {},
    viewModel: MainViewModel = koinInject(),
    localLibraryViewModel: LocalLibraryViewModel = koinInject(),
) {
    val localLibrary by localLibraryViewModel.library.collectAsState()
    val localLibraryReadFailed by localLibraryViewModel.readFailed.collectAsState()
    val localLibraryEnabled by localLibraryViewModel.enabled.collectAsState()
    val userDetail by viewModel.userDetail.collectAsState()
    val userLevel by viewModel.userLevel.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val playlistsLoaded by viewModel.userPlaylistsLoaded.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val accountLoadError by viewModel.accountLoadError.collectAsState()
    val userDetailLoadError by viewModel.userDetailLoadError.collectAsState()
    val userLevelLoadError by viewModel.userLevelLoadError.collectAsState()
    val userPlaylistsLoadError by viewModel.userPlaylistsLoadError.collectAsState()
    val intelligenceLoadingPlaylistId by viewModel.intelligenceLoadingPlaylistId.collectAsState()
    val intelligenceError by viewModel.intelligenceError.collectAsState()
    val userDetailFromCache by viewModel.userDetailFromCache.collectAsState()
    val userLevelFromCache by viewModel.userLevelFromCache.collectAsState()
    val userPlaylistsFromCache by viewModel.userPlaylistsFromCache.collectAsState()
    val uid by viewModel.uid.collectAsState()
    val sessionExpired by viewModel.sessionExpired.collectAsState()
    var lastIntelligencePlaylistId by remember { mutableStateOf<Long?>(null) }
    val hasCachedContent = (userDetailFromCache && userDetail != null) ||
        (userLevelFromCache && userLevel?.data != null) || userPlaylistsFromCache
    val hasAccountContent = userDetail != null || playlistsLoaded
    val (created, saved) = remember(playlists, uid) { playlists.partition { it.creator.userId == uid } }
    val listState = rememberLazyListState()

    ExpressivePage(contentWindowInsets = WindowInsets.statusBars) {
        val gutter = LocalExpressivePageGutter.current
        val inset = Modifier.padding(horizontal = gutter)
        val barScroll = TopAppBarDefaults.pinnedScrollBehavior()
        Column(Modifier.fillMaxSize().nestedScroll(barScroll.nestedScrollConnection)) {
            ExpressivePageBar(
                title = strings.me,
                modifier = Modifier.padding(horizontal = gutter - ExpressiveLayout.PageHorizontalPadding),
                scrollBehavior = barScroll,
                actions = {
                    FilledTonalIconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(AppIcons.Settings, contentDescription = strings.settings)
                    }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // Only the viewport content reserves clearance, so it scrolls under the player.
                contentPadding = PaddingValues(top = 8.dp, bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
            ) {
                val profile = userDetail?.profile
                // The network refresh does not gate rendering of the cached status or playlists.
                if (profile != null || userLevel?.data != null) {
                    item(key = "user-hero") {
                        UserProfilePanel(
                            profile = profile,
                            userLevel = userLevel,
                            levelLoading = userLevel == null && userLevelLoadError == null && accountLoading,
                            levelLoadFailed = userLevel == null && userLevelLoadError != null,
                            onRetryLevel = viewModel::retryAccountData,
                            modifier = inset.padding(bottom = 12.dp),
                        )
                    }
                }
                if (hasCachedContent) {
                    item(key = "account-cache-hint") {
                        ExpressiveCacheHint(isRefreshing = accountLoading, modifier = inset.padding(bottom = 8.dp))
                    }
                }
                // Downloads and the local account remain available without a NetEase sign-in.
                // Disabling the local account hides its shortcut, not its persisted data.
                item(key = "library-device") {
                    val library = localLibrary
                    DeviceLibrarySection(
                        localSubtitle = if (localLibraryEnabled) {
                            when {
                                localLibraryReadFailed -> strings.localPlaylistsReadFailed
                                library == null -> strings.localPlaylistsShortcutSubtitle
                                library.playlists.isEmpty() -> strings.localPlaylistsEmptySubtitle
                                else -> buildString {
                                    append(strings.localPlaylistCount(library.userPlaylists.size))
                                    library.favorites?.let { append(strings.localLikesCountSuffix(it.tracks.size)) }
                                }
                            }
                        } else null,
                        onOpenDownloads = onOpenDownloads,
                        onOpenLocalLibrary = onOpenLocalLibrary,
                        modifier = inset,
                    )
                }
                when {
                    (accountLoadError != null || userDetailLoadError != null) && !hasAccountContent -> item(key = "account-error") {
                        ExpressiveStatePanel(
                            title = strings.accountDataLoadFailed,
                            message = (accountLoadError ?: userDetailLoadError)?.text.orEmpty(),
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = viewModel::retryAccountData,
                            modifier = inset.padding(top = 20.dp),
                        )
                    }
                    accountLoading && !hasAccountContent -> item(key = "account-loading") {
                        ExpressiveLoadingState(label = strings.accountAndPlaylistsLoading, modifier = inset.padding(top = 20.dp))
                    }
                    uid == 0L -> item(key = "login-hint") {
                        ExpressiveStatePanel(
                            title = if (sessionExpired) strings.signInExpiredTitle else strings.signInToSeePlaylistsTitle,
                            message = if (sessionExpired) strings.signInExpiredMessage else strings.signInToSeePlaylistsMessage,
                            icon = AppIcons.Person,
                            actionLabel = strings.signIn,
                            onAction = onOpenLogin,
                            modifier = inset.padding(top = 20.dp),
                        )
                    }
                    !hasAccountContent -> item(key = "profile-unavailable") {
                        ExpressiveStatePanel(
                            title = strings.profileUnavailableTitle,
                            message = strings.profileUnavailableMessage,
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = viewModel::retryAccountData,
                            modifier = inset.padding(top = 20.dp),
                        )
                    }
                    else -> {
                        if (intelligenceError != null) {
                            item(key = "intelligence-error") {
                                ExpressiveStatePanel(
                                    title = strings.heartbeatModeStartFailed,
                                    message = intelligenceError?.text.orEmpty(),
                                    icon = AppIcons.Favorite,
                                    tone = ExpressiveStateTone.Error,
                                    actionLabel = lastIntelligencePlaylistId?.let { strings.retry },
                                    onAction = lastIntelligencePlaylistId?.let { playlistId ->
                                        { viewModel.startIntelligenceMode(playlistId) }
                                    },
                                    modifier = inset.padding(top = 20.dp),
                                )
                            }
                        }
                        when {
                            userPlaylistsLoadError != null && !playlistsLoaded -> item(key = "playlist-error") {
                                ExpressiveStatePanel(
                                    title = strings.userPlaylistsLoadFailed,
                                    message = userPlaylistsLoadError?.text.orEmpty(),
                                    icon = AppIcons.Refresh,
                                    tone = ExpressiveStateTone.Error,
                                    actionLabel = strings.retry,
                                    onAction = viewModel::retryAccountData,
                                    modifier = inset.padding(top = 20.dp),
                                )
                            }
                            accountLoading && !playlistsLoaded -> item(key = "playlist-loading") {
                                ExpressiveLoadingState(label = strings.myPlaylistsLoading, modifier = inset.padding(top = 20.dp))
                            }
                            playlists.isEmpty() -> item(key = "playlist-empty") {
                                ExpressiveStatePanel(
                                    title = strings.noPlaylistsYetTitle,
                                    message = strings.noPlaylistsYetMessage,
                                    modifier = inset.padding(top = 20.dp),
                                )
                            }
                            else -> {
                                // Item identity is independent of viewport width or UI language.
                                // The shared row reflows its own actions instead of regrouping the list.
                                listOf("created" to created, "saved" to saved).forEach { (section, group) ->
                                    if (group.isEmpty()) return@forEach
                                    item(key = "playlist-heading-$section") {
                                        ExpressiveSectionTitle(
                                            text = if (section == "created") strings.createdPlaylists else strings.savedPlaylists,
                                            supportingText = strings.playlistSectionCount(group.size),
                                            modifier = inset.padding(top = 24.dp, bottom = 8.dp),
                                        )
                                    }
                                    itemsIndexed(group, key = { _, playlist -> playlist.id }) { index, playlist ->
                                        val isFavorite = isFavoritePlaylist(
                                            specialType = playlist.specialType,
                                            name = playlist.name,
                                            creatorUserId = playlist.creator.userId,
                                            currentUserId = uid,
                                        )
                                        PlaylistCard(
                                            userPlaylistEach = playlist,
                                            specialCard = when {
                                                isFavorite -> "fav"
                                                playlist.name.contains("私人雷达") -> "radar"
                                                else -> "no"
                                            },
                                            index = index,
                                            count = group.size,
                                            onClick = { onOpenPlaylist(playlist.id) },
                                            onSpecialClick = if (isFavorite) {
                                                {
                                                    lastIntelligencePlaylistId = playlist.id
                                                    viewModel.startIntelligenceMode(playlist.id)
                                                }
                                            } else null,
                                            specialActionLoading = intelligenceLoadingPlaylistId == playlist.id,
                                            showCreator = playlist.creator.userId != uid,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UserProfilePanel(
    profile: UserDetailProfile?,
    userLevel: UserLevelResponse?,
    levelLoading: Boolean,
    levelLoadFailed: Boolean,
    onRetryLevel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val avatar = profile?.avatarUrl?.takeIf(String::isNotBlank)
    val background = profile?.backgroundUrl?.takeIf(String::isNotBlank)
    val artworkAccent = rememberArtworkAccent(requestKey = avatar, label = "profileAvatarAccent")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showBackground = maxWidth >= 640.dp && background != null
            Row(
                modifier = Modifier.fillMaxWidth().padding(if (showBackground) 24.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveArtwork(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            containerColor = artworkAccent.palette.container,
                            contentColor = artworkAccent.palette.onContainer,
                            placeholderIcon = AppIcons.Person,
                            onImageLoaded = artworkAccent.extract,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(strings.neteaseCloudMusic, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(
                                text = profile?.nickname?.takeIf(String::isNotBlank) ?: strings.me,
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                    UserLevelStatus(userLevelDisplay(userLevel), levelLoading, levelLoadFailed, artworkAccent.palette.accent, onRetryLevel)
                }
                if (showBackground) {
                    // Decorative picture, never the background of text or system bars.
                    ExpressiveArtwork(
                        model = background,
                        contentDescription = null,
                        modifier = Modifier.size(width = 208.dp, height = 144.dp),
                        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner - 24.dp),
                        placeholderIcon = AppIcons.Person,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserLevelStatus(
    display: UserLevelDisplay?,
    loading: Boolean,
    failed: Boolean,
    accent: Color,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = scheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = display?.summary ?: if (loading) strings.userLevelLoading else strings.userLevelUnavailable,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (display != null) {
                ExpressiveWavyProgress(
                    progress = { display.progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = legibleAccentFor(accent, container, scheme.surface, minimumContrast = 3f),
                    trackColor = scheme.surfaceContainerHighest,
                )
                Text(display.progressLabel, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                display.nextLevelLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            } else if (failed) {
                TextButton(onClick = onRetry) { Text(strings.retry) }
            }
        }
    }
}

@Composable
private fun DeviceLibrarySection(
    localSubtitle: String?,
    onOpenDownloads: () -> Unit,
    onOpenLocalLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpressiveSectionTitle(text = strings.libraryOnThisDevice)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 600.dp && localSubtitle != null
            val download: @Composable (Shape, Modifier) -> Unit = { shape, shortcutModifier ->
                LibraryShortcut(
                    title = strings.downloadManagement,
                    subtitle = strings.downloadsShortcutSubtitle,
                    icon = AppIcons.Download,
                    onClick = onOpenDownloads,
                    shape = shape,
                    modifier = shortcutModifier,
                )
            }
            val local: @Composable (Shape, Modifier) -> Unit = { shape, shortcutModifier ->
                if (localSubtitle != null) {
                    LibraryShortcut(
                        title = strings.localPlaylists,
                        subtitle = localSubtitle,
                        icon = AppIcons.QueueMusic,
                        onClick = onOpenLocalLibrary,
                        shape = shape,
                        modifier = shortcutModifier,
                    )
                }
            }
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shape = RoundedCornerShape(20.dp)
                    download(shape, Modifier.weight(1f))
                    local(shape, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ExpressiveLayout.ConnectedItemGap)) {
                    download(connectedListItemShape(0, if (localSubtitle == null) 1 else 2), Modifier.fillMaxWidth())
                    local(connectedListItemShape(1, 2), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryShortcut(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveFeatureIcon(icon, containerColor = scheme.surfaceContainerHighest, contentColor = scheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}
