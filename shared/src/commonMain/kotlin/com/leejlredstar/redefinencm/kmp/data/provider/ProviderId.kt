package com.leejlredstar.redefinencm.kmp.data.provider

/**
 * A music service the app can aggregate.
 *
 * [key] is persisted — in composite ids, in settings keys, and eventually in cache tables — so it
 * must stay stable even if the enum entry is renamed.
 */
enum class MusicProviderId(val key: String, val displayName: String) {
    NETEASE("ncm", "网易云音乐"),
    QQ("qq", "QQ音乐"),
    ;

    companion object {
        /** The provider every id belonged to before the app knew what a provider was. */
        val Legacy: MusicProviderId = NETEASE

        fun fromKey(key: String): MusicProviderId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A track, playlist or album id qualified by the service it came from.
 *
 * Two providers' id spaces would otherwise collide silently: NetEase ids are numeric, QQ's
 * `songmid` values are opaque strings, and nothing stops the two from overlapping once both are
 * stringified. Serialized form is `"ncm:123456"` / `"qq:0039MnYb0qxYhV"`.
 *
 * `MediaInfo.id` is already a `String` and the player reaches streams through the opaque
 * `placeholderUri` → `StreamUrlResolver` indirection, so composite ids travel through the player
 * unchanged and it never learns that providers exist.
 */
data class ProviderItemId(
    val provider: MusicProviderId,
    val rawId: String,
) {
    init {
        require(rawId.isNotEmpty()) { "rawId must not be empty" }
    }

    /** The NetEase id as a number, or null when this id is not a numeric NetEase id. */
    val neteaseIdOrNull: Long?
        get() = if (provider == MusicProviderId.NETEASE) rawId.toLongOrNull() else null

    override fun toString(): String = "${provider.key}$Separator$rawId"

    companion object {
        const val Separator: Char = ':'

        fun netease(id: Long): ProviderItemId = ProviderItemId(MusicProviderId.NETEASE, id.toString())

        fun qq(songMid: String): ProviderItemId = ProviderItemId(MusicProviderId.QQ, songMid)

        /**
         * Parses a serialized id, tolerating ids written before providers existed.
         *
         * A bare id with no recognised prefix is read as NetEase rather than rejected: the player
         * queue, the download queue and a year of cached rows are all full of bare numeric ids,
         * and they must keep resolving. An unknown prefix is *not* silently coerced — `"spotify:1"`
         * returns null so a future provider's ids cannot be mistaken for NetEase's.
         */
        fun parseOrNull(value: String): ProviderItemId? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null

            val separatorAt = trimmed.indexOf(Separator)
            // No separator at all is the legacy form. A leading separator is malformed: it names
            // an empty provider, and reading it as a legacy id would invent a raw id that starts
            // with a colon and can never resolve.
            if (separatorAt < 0) return legacyOrNull(trimmed)
            if (separatorAt == 0) return null

            val prefix = trimmed.substring(0, separatorAt)
            val rawId = trimmed.substring(separatorAt + 1)
            if (rawId.isEmpty()) return null

            val provider = MusicProviderId.fromKey(prefix)
                ?: return if (looksLikeProviderPrefix(prefix)) null else legacyOrNull(trimmed)
            return ProviderItemId(provider, rawId)
        }

        /**
         * A prefix is only treated as a provider key when it looks like one. Without this an id
         * that happens to contain a colon would be rejected outright instead of falling back to
         * the legacy reading.
         */
        private fun looksLikeProviderPrefix(prefix: String): Boolean =
            prefix.length <= MaxProviderKeyLength && prefix.all { it in 'a'..'z' }

        private fun legacyOrNull(value: String): ProviderItemId? =
            if (value.isEmpty()) null else ProviderItemId(MusicProviderId.Legacy, value)

        private const val MaxProviderKeyLength = 8
    }
}

/** Convenience for the many call sites holding a raw `MediaInfo.id`. */
fun String.toProviderItemIdOrNull(): ProviderItemId? = ProviderItemId.parseOrNull(this)

/**
 * The provider a raw id belongs to, defaulting to NetEase for ids written before providers
 * existed. Use this for dispatch; use [toProviderItemIdOrNull] when the raw id is needed too.
 */
fun String.providerIdOrLegacy(): MusicProviderId =
    ProviderItemId.parseOrNull(this)?.provider ?: MusicProviderId.Legacy
