package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The observable half of [PlatformPlayer], which every backend held identically.
 *
 * All five implementations declared the same eleven `MutableStateFlow` / `override val` pairs and
 * fanned a queue snapshot out to the same five of them. None of the playback control is here:
 * the backends genuinely disagree about who owns the queue — ExoPlayer reads its own timeline,
 * the Desktop player holds revision-stamped claims, and the iOS, Web and in-memory players own a
 * [PlayQueue] — so unifying `play()` would mean rewriting three audio stacks rather than
 * deduplicating them.
 */
abstract class BasePlatformPlayer(
    initialVolume: Float = 1f,
) : PlatformPlayer {

    protected val _state = MutableStateFlow(PlayerState.IDLE)
    final override val state: StateFlow<PlayerState> = _state.asStateFlow()

    protected val _position = MutableStateFlow(0L)
    final override val position: StateFlow<Long> = _position.asStateFlow()

    protected val _isPlaying = MutableStateFlow(false)
    final override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    protected val _duration = MutableStateFlow(-1L)
    final override val duration: StateFlow<Long> = _duration.asStateFlow()

    protected val _currentMedia = MutableStateFlow<MediaInfo?>(null)
    final override val currentMedia: StateFlow<MediaInfo?> = _currentMedia.asStateFlow()

    protected val _playbackOccurrence = MutableStateFlow(0L)
    final override val playbackOccurrence: StateFlow<Long> = _playbackOccurrence.asStateFlow()

    protected val _queue = MutableStateFlow<List<MediaInfo>>(emptyList())
    final override val queue: StateFlow<List<MediaInfo>> = _queue.asStateFlow()

    protected val _currentIndex = MutableStateFlow(-1)
    final override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    protected val _shuffleEnabled = MutableStateFlow(false)
    final override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    protected val _queueSnapshot = MutableStateFlow(PlayerQueueSnapshot())
    final override val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot.asStateFlow()

    protected val _volume = MutableStateFlow(normalizePlayerVolume(initialVolume))
    final override val volume: StateFlow<Float> = _volume.asStateFlow()

    /**
     * Fans one timeline snapshot out to the five queue-derived flows.
     *
     * [_duration] is deliberately untouched: ExoPlayer reports a decoder duration that queue
     * metadata must not overwrite. Backends whose duration only ever comes from the queue call
     * [publishDurationFromMedia] alongside this.
     */
    protected fun publishQueueSnapshot(snapshot: PlayerQueueSnapshot) {
        _queueSnapshot.value = snapshot
        _queue.value = snapshot.items
        _currentIndex.value = snapshot.currentIndex
        _currentMedia.value = snapshot.currentMedia
        _shuffleEnabled.value = snapshot.shuffleEnabled
    }

    /**
     * Publishes [model] as the queue every surface reads, and returns what was published.
     *
     * The three copies of this — iOS, Web and the in-memory player, the backends that own a
     * [PlayQueue] rather than reading a native timeline — each derived the visible items, the
     * highlight position and the current track from the model in the same three lines. Deriving
     * them together from one model on every publish is the shuffle invariant; a fourth backend
     * written by copying is how it would be lost.
     */
    protected fun publishQueue(model: PlayQueue<MediaInfo>): PlayerQueueSnapshot {
        val snapshot = PlayerQueueSnapshot(
            items = model.itemsInPlayOrder,
            currentIndex = model.positionInPlayOrder,
            currentMedia = model.currentItem,
            shuffleEnabled = model.shuffleEnabled,
        )
        publishQueueSnapshot(snapshot)
        publishDurationFromMedia(snapshot.currentMedia)
        return snapshot
    }

    /** Publishes the queue's own idea of the duration, or -1 for "not known yet". */
    protected fun publishDurationFromMedia(media: MediaInfo?) {
        _duration.value = media?.duration?.takeIf { it > 0L } ?: -1L
    }

    /**
     * Normalises [volume], hands it to the backend through [applyToBackend], and persists it
     * only when the stored whole-percent value actually changes — a drag across one percent
     * would otherwise write on every animation frame.
     */
    protected fun applyVolume(
        volume: Float,
        settings: PlatformSettings,
        applyToBackend: (Float) -> Unit,
    ) {
        val safeVolume = normalizePlayerVolume(volume)
        val oldPercent = playerVolumeToPercent(_volume.value)
        val newPercent = playerVolumeToPercent(safeVolume)
        _volume.value = safeVolume
        applyToBackend(safeVolume)
        if (newPercent != oldPercent) {
            settings.setLong(SettingKeys.PLAYER_VOLUME, newPercent)
        }
    }
}

/** The volume a previous session left behind, as the 0f..1f the player contract uses. */
internal fun PlatformSettings.persistedPlayerVolume(): Float =
    playerVolumeFromPercent(getLong(SettingKeys.PLAYER_VOLUME, DEFAULT_PLAYER_VOLUME_PERCENT))
