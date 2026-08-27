package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId

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
