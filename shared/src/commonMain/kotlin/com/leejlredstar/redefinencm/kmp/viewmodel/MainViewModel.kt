package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.uiText
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getLongAsync
import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import com.leejlredstar.redefinencm.kmp.data.toPlayerStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.MergedSearchEntry
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProvider
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.mergeSameSongs
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderSearchResults
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.data.provider.interleaved
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.player.PlaybackAccountVerificationEvent
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingCoordinator
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestorer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.player.playbackCredentialKey
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.cookieFingerprint
import com.leejlredstar.redefinencm.kmp.util.currentReleaseVersion
import com.leejlredstar.redefinencm.kmp.util.fetchLatestReleaseTag
import com.leejlredstar.redefinencm.kmp.util.isNewerReleaseVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun accountCookieFingerprint(cookie: String): Long = cookieFingerprint(cookie)

internal fun isCachedAccountIdentityValid(
    cookie: String,
    uid: Long,
    fingerprint: Long,
): Boolean =
    cookie.isNotBlank() && uid != 0L && fingerprint == accountCookieFingerprint(cookie)

/** What asking the service "who is signed in" came back with. */
internal sealed interface AccountLookup {
    data class SignedIn(val uid: Long) : AccountLookup

    /** No credential is stored: nobody is signed in. */
    data object SignedOut : AccountLookup

    /** A credential is stored and the service answered that it belongs to nobody any more. */
    data object Expired : AccountLookup

    /** A credential is stored and the service could not be asked. */
    data object Unreachable : AccountLookup
}

/**
 * [response] is null when the request failed. An answer with no account, or the service's "needs
 * sign-in" code, means the credential is not accepted any more; any other code is the service
 * failing or refusing the request itself, which says nothing about the account. Before these were
 * told apart one failed request showed a signed-in user the sign-in prompt, with nothing to retry.
 */
internal fun classifyAccountLookup(cookie: String, response: UserAccount?): AccountLookup {
    val uid = response?.takeIf { it.code == ACCOUNT_LOOKUP_OK }?.account?.id ?: 0L
    return when {
        uid != 0L -> AccountLookup.SignedIn(uid)
        cookie.isBlank() -> AccountLookup.SignedOut
        response == null -> AccountLookup.Unreachable
        response.code == ACCOUNT_LOOKUP_OK || response.code == ACCOUNT_LOOKUP_NEEDS_SIGN_IN -> AccountLookup.Expired
        else -> AccountLookup.Unreachable
    }
}

/**
 * Whether the answer about a UID stored at sign-in ends that session. Only the service saying the
 * credential belongs to nobody does: offline, or with the service failing, the stored account
 * stays usable.
 */
internal fun endsStoredSession(lookup: AccountLookup): Boolean = lookup == AccountLookup.Expired

private const val ACCOUNT_LOOKUP_OK = 200

/** NeteaseCloudMusicApi's answer for a request that needs an account it was not given. */
private const val ACCOUNT_LOOKUP_NEEDS_SIGN_IN = 301

/** The account service did not answer; the account pages offer to ask again. */
internal class AccountLookupUnreachableException : IllegalStateException()

internal fun shouldApplyPlaybackVerification(
    currentUid: Long,
    currentCredentialKey: Long?,
    lastAppliedGeneration: Long?,
    event: PlaybackAccountVerificationEvent,
): Boolean = currentUid == event.uid &&
    currentCredentialKey == event.credentialKey &&
    (lastAppliedGeneration ?: Long.MIN_VALUE) < event.reportingGeneration

/**
 * Where one provider's search results continue.
 *
 * Each provider pages on its own. One that failed is asked for the page it missed, since
 * skipping it would lose its first results for good, and one that has run out is not asked
 * again.
 */
internal data class SearchSuggestions(val query: String, val items: List<String>)

internal data class SearchCursor(
    val nextOffset: Int,
    val exhausted: Boolean,
    val failed: Boolean,
) {
    val hasMore: Boolean get() = !exhausted

    companion object {
        fun after(requestedOffset: Int, results: ProviderSearchResults, pageSize: Int): SearchCursor =
            if (results.failed) {
                SearchCursor(nextOffset = requestedOffset, exhausted = false, failed = true)
            } else {
                SearchCursor(
                    nextOffset = requestedOffset + pageSize,
                    exhausted = results.tracks.size < pageSize,
                    failed = false,
                )
            }
    }
}

/**
 * Ported from the original Android MainViewModel.
 * KMP-compatible: uses PlatformPlayer instead of MediaController,
 * PlatformSettings instead of DataStoreManager, kotlinx.serialization instead of Gson.
 */
class MainViewModel(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val player: PlatformPlayer,
    private val downloadManager: SongDownloadManager,
    private val playbackReportingCoordinator: PlaybackReportingCoordinator,
    private val playerStatusRestorer: PlayerStatusRestorer,
    private val providers: MusicProviderRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val accountCredentialKey = MutableStateFlow<Long?>(null)
    private val lastAppliedPlaybackVerification = mutableMapOf<Long, Long>()
    private var lastSavedPlayerStatus: PlayerStatus? = null
    private val playerStatusSaveMutex = Mutex()
    val playerStatusSaveError = MutableStateFlow<UiText?>(null)
    val playerStatusLoadError = MutableStateFlow<UiText?>(null)
    val playerStatusRestoreState: StateFlow<PlayerStatusRestoreState> = playerStatusRestorer.state

    // ── User ──
    private val _uid = MutableStateFlow(0L)
    val uid: StateFlow<Long> = _uid.asStateFlow()

    val userDetail = MutableStateFlow<UserDetail?>(null)
    val userLevel = MutableStateFlow<UserLevelResponse?>(null)
    val userPlaylists = MutableStateFlow<List<UserPlaylistEach>>(emptyList())
    val userPlaylistsLoaded = MutableStateFlow(false)
    val accountLoading = MutableStateFlow(false)
    val accountLoadError = MutableStateFlow<UiText?>(null)
    /** A credential is stored but the service no longer accepts it: the prompt says sign in again. */
    val sessionExpired = MutableStateFlow(false)
    val userDetailLoadError = MutableStateFlow<UiText?>(null)
    val userLevelLoadError = MutableStateFlow<UiText?>(null)
    val userPlaylistsLoadError = MutableStateFlow<UiText?>(null)
    val userDetailFromCache = MutableStateFlow(false)
    val userLevelFromCache = MutableStateFlow(false)
    val userPlaylistsFromCache = MutableStateFlow(false)
    private val accountGeneration = MutableStateFlow(0L)
    private var accountJob: Job? = null
    private var startupVipGrowthClaimAttempted = false
    private var startupVipGrowthClaimJob: Job? = null

    // ── Intelligence playback ──
    val intelligenceLoadingPlaylistId = MutableStateFlow<Long?>(null)
    val intelligenceError = MutableStateFlow<UiText?>(null)
    private val intelligenceGeneration = MutableStateFlow(0L)
    private var intelligenceJob: Job? = null

    // ── Playlist ──
    val playlistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val playlistSongs = MutableStateFlow<PlaylistTrackAll?>(null)
    val playlistLoading = MutableStateFlow(false)
    val playlistLoadError = MutableStateFlow<UiText?>(null)
    val playlistDetailLoadError = MutableStateFlow<UiText?>(null)
    val playlistDetailFromCache = MutableStateFlow(false)
    val playlistSongsFromCache = MutableStateFlow(false)
    private val playlistGeneration = MutableStateFlow(0L)
    private val activePlaylistId = MutableStateFlow<Long?>(null)
    private var playlistJob: Job? = null

    // ── Recommend ──
    val recommendResource = MutableStateFlow<RecommendResource?>(null)
    val recommendSongs = MutableStateFlow<RecommendSongs?>(null)
    val recommendResourceLoadError = MutableStateFlow<UiText?>(null)
    val recommendSongsLoadError = MutableStateFlow<UiText?>(null)
    val recommendResourceFromCache = MutableStateFlow(false)
    val recommendSongsFromCache = MutableStateFlow(false)

    // ── Search ──
    /** Every provider's hits, grouped, for the per-provider view. */
    val searchGroups = MutableStateFlow<List<ProviderSearchResults>>(emptyList())
    /** The same hits interleaved, for the merged view. */
    val searchResults = MutableStateFlow<List<ProviderTrack>>(emptyList())
    val searchAggregationMode = MutableStateFlow(LibraryAggregationMode.Default)
    /** Whether the merged view folds the same song from several providers into one row. */
    val searchMergeSameSongs = MutableStateFlow(SettingKeys.MERGE_SAME_SONGS_DEFAULT)
    /** The merged view's rows: [searchResults], with the same song folded when that is on. */
    val searchEntries: StateFlow<List<MergedSearchEntry>>
    /** Providers whose last page failed; the page offers to ask them again. */
    val searchFailedProviders = MutableStateFlow<List<MusicProviderId>>(emptyList())
    internal val searchSuggestions = MutableStateFlow<SearchSuggestions?>(null)
    private var suggestionQuery: String? = null
    private var searchGeneration = 0L
    /** Whether a provider may have results after the pages shown. */
    val searchHasMore = MutableStateFlow(false)
    val searchLoadingMore = MutableStateFlow(false)
    val searchMoreError = MutableStateFlow<UiText?>(null)
    val searchHistory = MutableStateFlow<List<String>>(emptyList())
    val hotSearches = MutableStateFlow<List<SearchHotItem>>(emptyList())
    private val historyMutex = Mutex()
    /** Where each provider's next page starts, and whether it has one. */
    private var searchCursors: Map<MusicProviderId, SearchCursor> = emptyMap()
    private var searchMoreJob: Job? = null
    private var hotSearchJob: Job? = null
    val searchLoading = MutableStateFlow(false)
    val searchSubmittedQuery = MutableStateFlow<String?>(null)
    val searchError = MutableStateFlow<UiText?>(null)
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    // ── Update check（原版 SplashActivity.checkAppUpdate）──
    val updateMessage = MutableStateFlow<String?>(null)

    init {
        searchEntries = combine(searchResults, searchAggregationMode, searchMergeSameSongs) {
                results, mode, merge ->
            if (mode == LibraryAggregationMode.MERGED && merge) {
                results.mergeSameSongs()
            } else {
                results.map { MergedSearchEntry(it) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        scope.launch(Dispatchers.Default) {
            historyMutex.withLock { searchHistory.value = storedSearchHistory() }
        }
        loadAccount(
            clearPersistedAccount = false,
            claimVipGrowthPointsOnSuccess = true,
        )
        observePlayerStatusRestoration()
        downloadManager.syncWithLocalLibrary()
        initPlayerStatusAutosave()
        observePlaybackVerification()
        checkAppUpdate()
    }

    private fun observePlaybackVerification() {
        scope.launch {
            playbackReportingCoordinator.verificationEvents.collect { event ->
                if (
                    !shouldApplyPlaybackVerification(
                        currentUid = _uid.value,
                        currentCredentialKey = accountCredentialKey.value,
                        lastAppliedGeneration = lastAppliedPlaybackVerification[event.credentialKey],
                        event = event,
                    )
                ) {
                    return@collect
                }
                lastAppliedPlaybackVerification[event.credentialKey] = event.reportingGeneration
                event.userLevel?.let { refreshedLevel ->
                    userLevel.value = refreshedLevel
                    userLevelFromCache.value = false
                    userLevelLoadError.value = null
                }
            }
        }
    }

    /** checkUpdate 设置开启时，比较 GitHub 最新 release tag 与本地版本，不同则提示。 */
    private fun checkAppUpdate() {
        scope.launch(Dispatchers.Default) {
            settings.awaitLoaded()
            if (!settings.getBooleanAsync(SettingKeys.CHECK_UPDATE, false)) return@launch
            // 完整构建版本带提交 hash（v0.0.1.412ae548）；更新检查只比较发布基线 tag。
            val current = currentReleaseVersion()
            val latest = fetchLatestReleaseTag() ?: return@launch
            if (isNewerReleaseVersion(latest, current)) {
                updateMessage.value = strings.updateAvailable(latest)
            }
        }
    }

    /** Checks for a newer release now and always reports the outcome, whatever the setting. */
    fun checkForUpdatesNow() {
        scope.launch(Dispatchers.Default) {
            val current = currentReleaseVersion()
            val latest = fetchLatestReleaseTag()
            updateMessage.value = when {
                latest == null -> strings.updateCheckFailed
                isNewerReleaseVersion(latest, current) -> strings.updateAvailable(latest)
                else -> strings.alreadyLatestVersion(current)
            }
        }
    }

    fun consumeUpdateMessage() {
        updateMessage.value = null
    }

    // ── 播放状态持久化（原版 savePlayerStatus / restorePlayerStatus）──

    fun savePlayerStatus() {
        val status = buildPlayerStatus() ?: return
        scope.launch(Dispatchers.Default) { savePlayerStatus(status) }
    }

    private fun buildPlayerStatus(): PlayerStatus? {
        val queueSnapshot = player.queueSnapshot.value
        // Before/after a failed restore an empty player is not evidence that the user cleared it.
        // After a successful restore, clearing must be saved or the old queue returns on restart.
        if (queueSnapshot.items.isEmpty() && playerStatusRestorer.state.value !is PlayerStatusRestoreState.Restored) return null
        return queueSnapshot.toPlayerStatus(
            position = player.position.value,
            isPlaying = player.isPlaying.value,
        )
    }

    private suspend fun savePlayerStatus(status: PlayerStatus) {
        playerStatusSaveMutex.withLock {
            if (status == lastSavedPlayerStatus) return
            repo.savePlayerStatus(status)
                .onSuccess {
                    lastSavedPlayerStatus = status
                    playerStatusSaveError.value = null
                }
                .onFailure { failure ->
                    playerStatusSaveError.value = failure.message?.let(::uiText) ?: UiText { it.playerStatusSaveFailed }
                }
        }
    }

    private suspend fun saveCurrentPlayerStatus() {
        savePlayerStatus(buildPlayerStatus() ?: return)
    }

    private fun initPlayerStatusAutosave() {
        scope.launch(Dispatchers.Default) {
            combine(player.queueSnapshot, player.isPlaying) { _, _ -> Unit }
                .drop(1)
                .collect { saveCurrentPlayerStatus() }
        }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                // Position-only changes are checkpointed periodically. Queue, track, shuffle and
                // play/pause changes are persisted immediately by the flow above.
                delay(PLAYER_POSITION_CHECKPOINT_MS)
                saveCurrentPlayerStatus()
            }
        }
    }

    private fun observePlayerStatusRestoration() {
        scope.launch {
            playerStatusRestorer.state.collect { state ->
                when (state) {
                    PlayerStatusRestoreState.Loading -> Unit
                    is PlayerStatusRestoreState.Restored -> {
                        lastSavedPlayerStatus = state.status
                        playerStatusLoadError.value = null
                    }
                    is PlayerStatusRestoreState.Failed -> {
                        playerStatusLoadError.value = state.message?.let(::uiText)
                    }
                }
            }
        }
    }

    /** The account's UID, and whether it was read from settings rather than from the service. */
    private data class ResolvedUid(val uid: Long, val fromSettings: Boolean)

    private suspend fun resolveUid(ignorePersistedUid: Boolean, cookie: String): ResolvedUid {
        if (!ignorePersistedUid) {
            val cachedUid = settings.getLongAsync(SettingKeys.UID, 0L)
            val cachedFingerprint = settings.getLongAsync(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
            if (isCachedAccountIdentityValid(cookie, cachedUid, cachedFingerprint)) {
                return ResolvedUid(cachedUid, fromSettings = true)
            }
        }
        val uid = when (val lookup = classifyAccountLookup(cookie, repo.getUserAccount())) {
            is AccountLookup.SignedIn -> lookup.uid
            AccountLookup.SignedOut -> 0L
            AccountLookup.Expired -> {
                sessionExpired.value = true
                0L
            }
            // Caught by loadAccount, which shows the account-data failure with a retry.
            AccountLookup.Unreachable -> throw AccountLookupUnreachableException()
        }
        return ResolvedUid(uid, fromSettings = false)
    }

    /**
     * A UID read from settings was stored when the user signed in, and the session behind it may
     * have ended since (a password change, signing out on every device). Only the account
     * service says so, and it was asked only at sign-in: endpoints that need an account answer
     * 301, which the caches swallow, so old recommendations stayed and retrying never helped.
     * It is asked here, in the background; only an answer that the credential belongs to nobody
     * any more signs out. Offline, the stored account stays usable.
     */
    private suspend fun verifyStoredSession(generation: Long, cookie: String, storedUid: Long) {
        if (!endsStoredSession(classifyAccountLookup(cookie, repo.getUserAccount()))) return
        if (accountGeneration.value != generation || _uid.value != storedUid) return
        settings.setLong(SettingKeys.UID, 0L)
        settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
        settings.flush()
        if (accountGeneration.value != generation || _uid.value != storedUid) return
        accountCredentialKey.value = null
        _uid.value = 0L
        sessionExpired.value = true
    }

    /** Login/account changes invalidate all work started for the previous credential. */
    private fun loadAccount(
        clearPersistedAccount: Boolean,
        claimVipGrowthPointsOnSuccess: Boolean = false,
    ) {
        cancelIntelligenceRequest()
        val generation = accountGeneration.updateAndGet { it + 1L }
        accountJob?.cancel()
        if (clearPersistedAccount) {
            accountCredentialKey.value = null
            startupVipGrowthClaimJob?.cancel()
            startupVipGrowthClaimJob = null
        }
        accountLoading.value = true
        accountLoadError.value = null
        sessionExpired.value = false
        userDetailLoadError.value = null
        userLevelLoadError.value = null
        userPlaylistsLoadError.value = null
        recommendResourceLoadError.value = null
        recommendSongsLoadError.value = null
        if (clearPersistedAccount) {
            _uid.value = 0L
            userDetail.value = null
            userLevel.value = null
            userPlaylists.value = emptyList()
            userPlaylistsLoaded.value = false
            recommendResource.value = null
            recommendSongs.value = null
            userDetailFromCache.value = false
            userLevelFromCache.value = false
            userPlaylistsFromCache.value = false
            recommendResourceFromCache.value = false
            recommendSongsFromCache.value = false
        }
        accountJob = scope.launch(Dispatchers.Default) {
            try {
                settings.awaitLoaded()
                val cookie = settings.getStringAsync(SettingKeys.COOKIE, "")
                val credentialCookie = HttpClientFactory.cleanCookie(cookie)
                val credentialKey = playbackCredentialKey(credentialCookie)
                if (accountGeneration.value != generation) return@launch
                accountCredentialKey.value = credentialKey
                if (clearPersistedAccount) {
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    settings.flush()
                    repo.clearAccountScopedCaches().getOrThrow()
                }

                val resolved = resolveUid(
                    ignorePersistedUid = clearPersistedAccount,
                    cookie = cookie,
                )
                val resolvedUid = resolved.uid
                ensureActive()
                if (accountGeneration.value != generation) return@launch
                if (resolvedUid == 0L) {
                    accountCredentialKey.value = null
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    settings.flush()
                    return@launch
                }
                settings.setLong(SettingKeys.UID, resolvedUid)
                settings.setLong(
                    SettingKeys.UID_COOKIE_FINGERPRINT,
                    accountCookieFingerprint(cookie),
                )
                settings.flush()
                ensureActive()
                if (accountGeneration.value != generation) return@launch
                _uid.value = resolvedUid
                if (claimVipGrowthPointsOnSuccess) {
                    scheduleStartupVipGrowthClaim(credentialCookie)
                }

                coroutineScope {
                    if (resolved.fromSettings) {
                        launch { verifyStoredSession(generation, cookie, resolvedUid) }
                    }
                    launch {
                        var emitted = false
                        repo.getUserDetail(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                val detail = emission.value
                                emitted = true
                                userDetail.value = detail
                                userDetailFromCache.value = emission.isFromCache
                                userDetailLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userDetailLoadError.value = UiText { it.userDetailLoadFailed }
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getUserLevel(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                userLevel.value = emission.value
                                userLevelFromCache.value = emission.isFromCache
                                userLevelLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userLevelLoadError.value = UiText { it.userLevelLoadFailed }
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getUserPlaylist(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                userPlaylists.value = emission.value.playlist
                                userPlaylistsLoaded.value = true
                                userPlaylistsFromCache.value = emission.isFromCache
                                userPlaylistsLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userPlaylistsLoadError.value = UiText { it.userPlaylistsLoadFailedRetry }
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getRecommendResource(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                recommendResource.value = emission.value
                                recommendResourceFromCache.value = emission.isFromCache
                                recommendResourceLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            recommendResourceLoadError.value = UiText { it.recommendedPlaylistsLoadFailed }
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getRecommendSongs(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                recommendSongs.value = emission.value
                                recommendSongsFromCache.value = emission.isFromCache
                                recommendSongsLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            recommendSongsLoadError.value = UiText { it.dailyPicksLoadFailedRetry }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (accountGeneration.value == generation) {
                    accountCredentialKey.value = null
                    _uid.value = 0L
                    userDetail.value = null
                    userLevel.value = null
                    userPlaylists.value = emptyList()
                    userPlaylistsLoaded.value = false
                    userLevelLoadError.value = null
                    recommendResource.value = null
                    recommendSongs.value = null
                    userDetailFromCache.value = false
                    userLevelFromCache.value = false
                    userPlaylistsFromCache.value = false
                    recommendResourceFromCache.value = false
                    recommendSongsFromCache.value = false
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    runCatching { settings.flush() }
                    accountLoadError.value = failure.message?.let(::uiText) ?: UiText { it.accountDataLoadFailed }
                }
            } finally {
                if (accountGeneration.value == generation) {
                    accountLoading.value = false
                }
            }
        }
    }

    private fun scheduleStartupVipGrowthClaim(credentialCookie: String) {
        if (startupVipGrowthClaimAttempted || credentialCookie.isBlank()) return
        startupVipGrowthClaimAttempted = true
        startupVipGrowthClaimJob = scope.launch(Dispatchers.Default) {
            repo.claimAllVipGrowthPoints(credentialCookie)
        }
    }

    /** 登录、退出或换号后调用。 */
    fun refreshAccount() {
        loadAccount(clearPersistedAccount = true)
    }

    /** Retry account-scoped cache/network reads without clearing the persisted identity. */
    fun retryAccountData() {
        loadAccount(clearPersistedAccount = false)
    }

    fun startIntelligenceMode(playlistId: Long) {
        val generation = intelligenceGeneration.updateAndGet { it + 1L }
        intelligenceJob?.cancel()
        intelligenceJob = null
        intelligenceLoadingPlaylistId.value = null
        intelligenceError.value = null

        val requestUid = _uid.value
        if (playlistId <= 0L) {
            intelligenceError.value = UiText { it.likedPlaylistIdInvalid }
            return
        }
        if (requestUid <= 0L) {
            intelligenceError.value = UiText { it.heartbeatModeSignInRequired }
            return
        }

        intelligenceLoadingPlaylistId.value = playlistId
        intelligenceJob = scope.launch(Dispatchers.Default) {
            try {
                val seed = selectIntelligenceSeed(repo.getLikedSongIds(requestUid))
                    ?: error(strings.likedSongsFetchFailed)
                ensureActive()
                if (!isCurrentIntelligenceRequest(generation, requestUid)) return@launch

                val response = repo.getIntelligenceList(
                    id = seed,
                    pid = playlistId,
                ) ?: error(strings.heartbeatModeListFetchFailed)
                val queue = buildIntelligenceQueue(
                    songInfos = response.data.orEmpty().map { it.songInfo },
                    playlistId = playlistId,
                )
                if (queue.isEmpty()) error(strings.heartbeatModeEmpty)
                ensureActive()
                if (!isCurrentIntelligenceRequest(generation, requestUid)) return@launch

                withContext(Dispatchers.Main) {
                    if (isCurrentIntelligenceRequest(generation, requestUid)) {
                        replaceQueueWithIntelligenceList(player, queue)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentIntelligenceRequest(generation, requestUid)) {
                    intelligenceError.value = failure.message?.let(::uiText) ?: UiText { it.heartbeatModeStartFailed }
                }
            } finally {
                if (isCurrentIntelligenceRequest(generation, requestUid)) {
                    intelligenceLoadingPlaylistId.value = null
                    if (currentCoroutineContext()[Job] == intelligenceJob) {
                        intelligenceJob = null
                    }
                }
            }
        }
    }

    private fun isCurrentIntelligenceRequest(
        generation: Long,
        uid: Long,
    ): Boolean = intelligenceGeneration.value == generation && _uid.value == uid

    private fun cancelIntelligenceRequest() {
        intelligenceGeneration.updateAndGet { it + 1L }
        intelligenceJob?.cancel()
        intelligenceJob = null
        intelligenceLoadingPlaylistId.value = null
        intelligenceError.value = null
    }

    /**
     * Shows [songlistID]'s page, loading it unless this playlist is already held or loading. A
     * page returned to (from the player, an artist page, a configuration change) keeps its rows
     * and so its scroll position, where loading again emptied the list and opened it at the top.
     */
    fun openPlaylist(songlistID: Long) {
        val held = activePlaylistId.value == songlistID &&
            (playlistLoading.value || (playlistDetail.value != null && playlistSongs.value != null))
        if (!held) fetchPlaylistDetail(songlistID)
    }

    fun fetchPlaylistDetail(songlistID: Long) {
        val generation = playlistGeneration.updateAndGet { it + 1L }
        playlistJob?.cancel()
        activePlaylistId.value = songlistID
        playlistDetail.value = null
        playlistSongs.value = null
        playlistLoading.value = true
        playlistLoadError.value = null
        playlistDetailLoadError.value = null
        playlistDetailFromCache.value = false
        playlistSongsFromCache.value = false
        playlistJob = scope.launch(Dispatchers.Default) {
            var detailEmitted = false
            var tracksEmitted = false
            try {
                coroutineScope {
                    launch {
                        repo.getPlaylistDetail(songlistID).collect { emission ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                detailEmitted = true
                                playlistDetail.value = emission.value
                                playlistDetailFromCache.value = emission.isFromCache
                            }
                        }
                    }
                    launch {
                        repo.getPlaylistTrackAll(songlistID).collect { emission ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                tracksEmitted = true
                                playlistSongs.value = emission.value
                                playlistSongsFromCache.value = emission.isFromCache
                            }
                        }
                    }
                }
                if (
                    !detailEmitted &&
                    playlistGeneration.value == generation &&
                    activePlaylistId.value == songlistID
                ) {
                    playlistDetailLoadError.value = UiText { it.playlistDetailLoadFailed }
                }
                if (
                    !tracksEmitted &&
                    playlistGeneration.value == generation &&
                    activePlaylistId.value == songlistID
                ) {
                    playlistLoadError.value = UiText { it.playlistTracksLoadFailed }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (playlistGeneration.value == generation) {
                    playlistLoadError.value = failure.message?.let(::uiText) ?: UiText { it.playlistLoadFailed }
                }
            } finally {
                if (playlistGeneration.value == generation) {
                    playlistLoading.value = false
                }
            }
        }
    }

    // ── Download（应用内下载队列；不使用系统 DownloadManager）──

    fun onDownloadPlaylistClick(songlistID: Long) {
        val currentSongs = if (activePlaylistId.value == songlistID) {
            playlistSongs.value?.songs.orEmpty()
        } else {
            emptyList()
        }
        if (currentSongs.isNotEmpty()) {
            downloadManager.enqueueSongs(currentSongs, songlistID)
        } else {
            downloadManager.enqueuePlaylist(songlistID)
        }
    }

    /** 上报歌单播放次数（原版播放歌单时调用）。 */
    fun updatePlaylistPlaycount(songlistID: Long) {
        scope.launch(Dispatchers.Default) { repo.updatePlaylistPlaycount(songlistID) }
    }

    // ── Search ──

    fun search(keyword: String) = startSearch(keyword.trim(), recordHistory = true)

    /** Same-process recreation keeps the current results, cursor, and any request in flight. */
    fun restoreSearch(query: String) {
        if (searchSubmittedQuery.value != query) startSearch(query, recordHistory = false)
    }

    private fun startSearch(query: String, recordHistory: Boolean) {
        if (query.isEmpty()) {
            clearSearch()
            return
        }
        val generation = ++searchGeneration
        searchJob?.cancel()
        clearSearchSuggestions()
        resetSearchPaging()
        searchLoading.value = true
        searchGroups.value = emptyList()
        searchResults.value = emptyList()
        searchSubmittedQuery.value = query
        searchError.value = null
        if (recordHistory) rememberSearch(query)
        // Publish on the main scope: a cancelled request must not update a new search, even when
        // the same query was explicitly retried. Network/provider work remains off the UI thread.
        searchJob = scope.launch {
            try {
                val groups = withContext(Dispatchers.Default) {
                    providers.searchAll(query, SearchPageSize)
                }
                ensureActive()
                if (generation != searchGeneration) return@launch
                searchAggregationMode.value = providers.aggregationMode()
                searchMergeSameSongs.value = settings.getBooleanAsync(
                    SettingKeys.MERGE_SAME_SONGS,
                    SettingKeys.MERGE_SAME_SONGS_DEFAULT,
                )
                ensureActive()
                if (generation != searchGeneration) return@launch
                searchCursors = groups.associate { group ->
                    group.provider to SearchCursor.after(requestedOffset = 0, group, SearchPageSize)
                }
                searchGroups.value = groups.filter { it.tracks.isNotEmpty() }
                searchResults.value = groups.interleaved()
                searchHasMore.value = searchCursors.values.any { it.hasMore }

                // "Nothing matched" and "the backend is down" are different answers, and with two
                // providers configured a partial failure must not be reported as either.
                val failed = groups.filter { it.failed }
                searchFailedProviders.value = failed.map { it.provider }
                searchError.value = when {
                    failed.isEmpty() -> null
                    failed.size == groups.size -> UiText { it.searchFailedRetry }
                    else -> failed.map { it.provider }.let { providers ->
                        UiText { s ->
                            s.searchPartiallyFailed(providers.joinToString(s.listSeparator) { p -> p.displayName })
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation == searchGeneration) searchError.value = UiText { it.searchFailedRetry }
            } finally {
                if (generation == searchGeneration) searchLoading.value = false
            }
        }
    }

    /**
     * Appends the next page of every provider that may have one, each from its own cursor: one
     * that failed is asked for the page it missed, and one that ran out is not asked at all.
     * Results already on screen keep their places; the new page is interleaved on its own and
     * added after them.
     */
    fun loadMoreSearchResults() {
        if (!searchHasMore.value) return
        loadSearchPages { it.hasMore }
    }

    /** Asks the providers whose last page failed for that page again. */
    fun retryFailedSearchProviders() {
        loadSearchPages { it.failed }
    }

    private fun loadSearchPages(which: (SearchCursor) -> Boolean) {
        val query = searchSubmittedQuery.value ?: return
        if (searchLoadingMore.value || searchLoading.value) return
        val offsets = searchCursors.filterValues(which).mapValues { (_, cursor) -> cursor.nextOffset }
        if (offsets.isEmpty()) return
        searchLoadingMore.value = true
        searchMoreError.value = null
        val generation = searchGeneration
        searchMoreJob = scope.launch {
            try {
                val groups = withContext(Dispatchers.Default) {
                    providers.searchPages(query, offsets, SearchPageSize)
                }
                ensureActive()
                if (generation != searchGeneration) return@launch
                val shownIds = searchResults.value.mapTo(mutableSetOf()) { it.id }
                val fresh = groups.map { group ->
                    group.copy(tracks = group.tracks.filterNot { it.id in shownIds })
                }
                searchCursors = searchCursors + groups.associate { group ->
                    group.provider to SearchCursor.after(offsets.getValue(group.provider), group, SearchPageSize)
                }
                searchResults.value = searchResults.value + fresh.interleaved()
                searchGroups.value = mergeSearchGroups(searchGroups.value, fresh)
                searchHasMore.value = searchCursors.values.any { it.hasMore }
                val failed = groups.filter { it.failed }
                searchFailedProviders.value = searchCursors.filterValues { it.failed }.keys.toList()
                searchMoreError.value = failed.takeIf { it.isNotEmpty() }
                    ?.map { it.provider }
                    ?.let { providers ->
                        UiText { s ->
                            s.searchMoreFailedFor(providers.joinToString(s.listSeparator) { p -> p.displayName })
                        }
                    }
                // Every failed provider has now answered, so the partial-failure line goes.
                if (searchFailedProviders.value.isEmpty()) searchError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation == searchGeneration) searchMoreError.value = UiText { it.searchMoreFailed }
            } finally {
                if (generation == searchGeneration) searchLoadingMore.value = false
            }
        }
    }

    private fun mergeSearchGroups(
        shown: List<ProviderSearchResults>,
        page: List<ProviderSearchResults>,
    ): List<ProviderSearchResults> {
        val merged = shown.associateBy { it.provider }.toMutableMap()
        page.filter { it.tracks.isNotEmpty() }.forEach { group ->
            val existing = merged[group.provider]
            merged[group.provider] = existing?.copy(tracks = existing.tracks + group.tracks) ?: group
        }
        // Registration order, as the first page had it.
        val order = (shown + page).map { it.provider }.distinct()
        return order.mapNotNull { merged[it] }
    }

    private fun resetSearchPaging() {
        searchMoreJob?.cancel()
        searchCursors = emptyMap()
        searchFailedProviders.value = emptyList()
        searchHasMore.value = false
        searchLoadingMore.value = false
        searchMoreError.value = null
    }

    /**
     * Most recent first, without repeats, at most [SearchHistoryLimit]. Read from the stored
     * list rather than the one on screen: on Android the settings load after the view model
     * starts, and adding to a list that had not loaded yet would overwrite the saved history.
     */
    private fun rememberSearch(query: String) {
        val entry = query.replace('\n', ' ').trim()
        if (entry.isEmpty()) return
        searchHistory.value = (listOf(entry) + searchHistory.value.filterNot { it == entry }).take(SearchHistoryLimit)
        scope.launch(Dispatchers.Default) {
            historyMutex.withLock {
                val stored = storedSearchHistory()
                val updated = (listOf(entry) + stored.filterNot { it == entry }).take(SearchHistoryLimit)
                searchHistory.value = updated
                runCatching { settings.setString(SettingKeys.SEARCH_HISTORY, updated.joinToString("\n")) }
            }
        }
    }

    private suspend fun storedSearchHistory(): List<String> =
        settings.getStringAsync(SettingKeys.SEARCH_HISTORY, "").lines().filter(String::isNotBlank)

    fun clearSearchHistory() {
        searchHistory.value = emptyList()
        scope.launch(Dispatchers.Default) {
            historyMutex.withLock {
                runCatching { settings.setString(SettingKeys.SEARCH_HISTORY, "") }
            }
        }
    }

    /** The hot-search chart, fetched once; an empty list when it could not be loaded. */
    fun loadHotSearches() {
        if (hotSearches.value.isNotEmpty() || hotSearchJob?.isActive == true) return
        hotSearchJob = scope.launch(Dispatchers.Default) {
            hotSearches.value = repo.searchHot().orEmpty().filter { it.searchWord.isNotBlank() }
        }
    }

    /** True only when the draft changes, not when the editor is recreated for the same draft. */
    fun requestSearchSuggestions(keyword: String): Boolean {
        val query = keyword.trim()
        if (query.isEmpty() || query == suggestionQuery) return false
        clearSearchSuggestions()
        suggestionQuery = query
        suggestionJob = scope.launch {
            delay(300)
            val response = withContext(Dispatchers.Default) { repo.searchSuggest(query) }
            ensureActive()
            if (suggestionQuery == query) {
                searchSuggestions.value = SearchSuggestions(
                    query,
                    response?.result?.allMatch.orEmpty().map { it.keyword }.distinct(),
                )
            }
        }
        return true
    }

    fun clearSearchSuggestions() {
        suggestionJob?.cancel()
        suggestionQuery = null
        searchSuggestions.value = null
    }

    fun clearSearch() {
        searchGeneration += 1
        searchJob?.cancel()
        clearSearchSuggestions()
        resetSearchPaging()
        searchGroups.value = emptyList()
        searchResults.value = emptyList()
        searchLoading.value = false
        searchSubmittedQuery.value = null
        searchError.value = null
    }

    fun onCleared() {
        scope.cancel()
    }

    private companion object {
        const val PLAYER_POSITION_CHECKPOINT_MS = 30_000L
        const val SearchPageSize = MusicProvider.DefaultSearchLimit
        const val SearchHistoryLimit = 20
    }
}
