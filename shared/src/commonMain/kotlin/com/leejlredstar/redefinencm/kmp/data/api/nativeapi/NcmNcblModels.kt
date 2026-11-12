package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

// NCBL models adapted to Kotlin on 2026-11-13 from api-enhanced's folltoshe-attributed
// AGPLv3 code. This adaptation is AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.

/** Whole-second scrobble inputs. Name, artist and vip are accepted but not emitted by upstream. */
data class NcmNcblParams(
    val songId: Long,
    val playedSeconds: Long,
    val totalSeconds: Long = playedSeconds,
    val sourceId: String = "",
    val source: String = "list",
    val name: String = "",
    val artist: String = "",
    val bitrate: Int = 320,
    val level: String = "exhigh",
    val vip: Boolean = false,
) {
    init {
        require(songId > 0) { "Invalid scrobble song ID" }
        require(playedSeconds > 0) { "Invalid scrobble play time" }
        require(totalSeconds > 0) { "Invalid scrobble total time" }
        require(bitrate > 0) { "Invalid scrobble bitrate" }
    }
}

/** Optional deterministic framing inputs for offline reference vectors; production leaves them null. */
class NcmNcblOptions(
    val maxFrame: Int = 0x8000,
    val keyA: ByteArray? = null,
    val uuid: ByteArray? = null,
    val baseSeq: UInt? = null,
) {
    override fun toString(): String = "NcmNcblOptions(maxFrame=$maxFrame, framingInputs=<redacted>)"
}

class NcmNcblApp(
    val id: String = "",
    val urs: String = "",
    val pid: String = "",
    val nsm: String = "1.0.0",
    val cid: String,
    val channel: String = "netease",
    val version: String = "3.1.35",
    val versionCode: String = "205293",
    val buildCode: String = "",
    val buildType: String = "release",
    val packageId: String = "",
) {
    override fun toString(): String = "NcmNcblApp(<redacted>)"
}

class NcmNcblDevice(
    val id: String = "",
    val ti: String = "",
    val sign: String = "",
    val model: String = "",
    val nnid: String = ",",
    val nuid: String = "",
    val csrf: String = "",
    val systemType: String = "pc",
    val systemVersion: String = "Microsoft-Windows-10-Professional-build-19045-64bit",
) {
    override fun toString(): String = "NcmNcblDevice(<redacted>)"
}

class NcmNcblAuth(
    val id: String = "",
    val token: String,
    val sessionId: String = "",
    val vipType: String = "",
) {
    override fun toString(): String = "NcmNcblAuth(<redacted>)"
}

class NcmNcblContext(
    val app: NcmNcblApp,
    val device: NcmNcblDevice,
    val auth: NcmNcblAuth,
    val startTime: Long,
    val processId: Int,
) {
    override fun toString(): String = "NcmNcblContext(<redacted>)"
}

/**
 * One credential snapshot and the two independent upstream records. This is not an upload attempt.
 * The transport must send PLV once, verify its successfiles response, then send PLD once. A failure
 * or ambiguous response must never cause an automatic retry, and cancellation must propagate.
 */
class NcmNcblPrepared internal constructor(
    val context: NcmNcblContext,
    val metaJson: String,
    val cookieHeader: String,
    val plvRecord: String,
    val pldRecord: String,
) {
    override fun toString(): String = "NcmNcblPrepared(<redacted>)"
}

class NcmNcblMultipart internal constructor(
    val boundary: String,
    val fileName: String,
    val body: ByteArray,
) {
    override fun toString(): String = "NcmNcblMultipart(fileName=$fileName, bodySize=${body.size})"
}

/** Already encoded bytes, ready for the Lead-owned HTTP transport. Never log headers or bodies. */
class NcmNcblUpload internal constructor(
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val fileName: String,
    val payloadSize: Int,
) {
    override fun toString(): String = "NcmNcblUpload(fileName=$fileName, payloadSize=$payloadSize)"
}
