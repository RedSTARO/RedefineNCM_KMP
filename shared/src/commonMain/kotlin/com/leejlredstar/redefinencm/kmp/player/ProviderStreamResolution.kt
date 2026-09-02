package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality

/**
 * Resolves a stream URL for ids belonging to a provider other than NetEase, and returns null for
 * NetEase's own ids so the caller continues down its existing path.
 *
 * Every platform player already owns NetEase-specific stream resolution: the local-download
 * lookup, the platform's playable-format filter, and the full NetEase quality ladder that the
 * backend accepts verbatim. Rerouting that through the registry would flatten those, so the split
 * is deliberate — the registry only takes over the ids the platform code cannot handle.
 *
 * Returning null rather than throwing matters here: three of the four platform resolvers used to
 * call `mediaId.toLong()`, which throws on a composite id like `qq:0039MnYb0qxYhV`, and a throw
 * on the player's IO thread at play time is a crash rather than a skipped track.
 */
suspend fun MusicProviderRegistry.streamUrlForForeignProvider(itemId: ProviderItemId): String? =
    if (itemId.provider == MusicProviderId.NETEASE) {
        null
    } else {
        streamUrl(itemId, playbackQuality())
    }

/**
 * The stream-resolution order every platform player follows.
 *
 * All four wrote this out: parse the media id, hand a foreign provider's id to the registry,
 * check whatever locally-downloaded copy the platform can play, then fall back to the NetEase
 * CDN at the configured quality. Only the last two steps differ between backends, so they are
 * the parameters — Desktop can only decode a subset of downloaded formats, Web has no local
 * store at all, and Desktop asks the backend for a different quality name than the others.
 *
 * Returning null means "nothing to play"; the caller treats that as a skipped selection rather
 * than an error.
 */
suspend fun resolveStreamUrl(
    mediaId: String,
    providers: MusicProviderRegistry,
    localAudioUri: suspend (neteaseId: Long) -> String?,
    onlineUrl: suspend (neteaseId: Long, quality: SoundQuality) -> String?,
    quality: () -> SoundQuality,
): String? {
    val itemId = mediaId.toProviderItemIdOrNull() ?: return null
    // Other providers carry their own quality ladders and have no local-download support yet.
    val neteaseId = itemId.neteaseIdOrNull ?: return providers.streamUrlForForeignProvider(itemId)
    localAudioUri(neteaseId)?.let { return it }
    return onlineUrl(neteaseId, quality())
}

/** The configured online playback quality, falling back when the stored name is unrecognised. */
fun PlatformSettings.onlinePlaybackQuality(): SoundQuality {
    val qualityName = getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
    return runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
}
