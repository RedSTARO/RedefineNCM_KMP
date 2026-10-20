package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.util.SoundQuality

/**
 * Resolves one media period's stream. Media3 opens a period's source again for every retry and
 * every seek, in the middle of the song. Each open gets a fresh link, since links expire, but
 * to the same file: the quality and the choice of local copy or stream made at the first open.
 * Otherwise a song started on Wi-Fi went on in another encoding after a switch to mobile data,
 * or from a download finished meanwhile, at the byte offset of the first.
 *
 * A failure is reported once per period rather than once per retry, so the screen does not
 * show it four times; a retry that then succeeds withdraws it. Its failures count only while
 * its player is the active one: the other player prepares the next track before anyone hears it.
 */
internal class PeriodStreamResolver(
    private val providers: MusicProviderRegistry,
    private val currentQuality: () -> SoundQuality,
    private val localAudioUri: suspend (mediaId: String, neteaseId: Long) -> String?,
    private val onlineUrl: suspend (neteaseId: Long, quality: SoundQuality) -> String?,
    private val isActive: () -> Boolean,
) : StreamUrlResolver {
    private var quality: SoundQuality? = null
    private var local: Boolean? = null
    private var failureReported = false

    override suspend fun resolve(mediaId: String): String? {
        val chosen = quality ?: currentQuality().also { quality = it }
        val reporting = isActive()
        var answeredLocally = false
        val url = resolveStreamUrl(
            mediaId = mediaId,
            providers = providers,
            localAudioUri = { id ->
                if (local == false) null else localAudioUri(mediaId, id)?.also { answeredLocally = true }
            },
            onlineUrl = onlineUrl,
            quality = { chosen },
            recordFailures = reporting && !failureReported,
            pinForeignQuality = true,
        )
        if (url == null) {
            if (reporting) failureReported = true
        } else {
            if (local == null) local = answeredLocally
            if (failureReported) {
                providers.clearStreamFailure(mediaId)
                failureReported = false
            }
        }
        return url
    }
}
