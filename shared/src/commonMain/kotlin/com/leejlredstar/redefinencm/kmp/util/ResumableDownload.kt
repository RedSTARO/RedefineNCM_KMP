package com.leejlredstar.redefinencm.kmp.util

internal sealed interface ResumableDownloadDecision {
    data class Append(
        val offset: Long,
        val totalBytes: Long,
    ) : ResumableDownloadDecision

    data class Restart(
        val totalBytes: Long?,
    ) : ResumableDownloadDecision

    data class RetryWithoutRange(
        val reason: String,
    ) : ResumableDownloadDecision

    data class Reject(
        val reason: String,
    ) : ResumableDownloadDecision
}

internal enum class ResumableValidatorKind {
    ETag,
    LastModified,
}

internal data class ResumableEntityValidator(
    val kind: ResumableValidatorKind,
    val value: String,
)

internal data class ResumableDownloadMetadata(
    val validator: ResumableEntityValidator?,
    val authoritativeTotalBytes: Long?,
    val representationKey: String,
    val fileExtension: String,
)

/**
 * Decide how an HTTP response may be written to a persistent partial download.
 *
 * A partial response is appendable only when its Content-Range starts exactly at the
 * requested offset and describes a self-consistent, bounded byte range. A full 200 response
 * deliberately restarts from byte zero because the server ignored (or did not receive) Range.
 */
internal fun decideResumableDownload(
    requestedOffset: Long,
    responseCode: Int,
    contentRange: String?,
    contentLength: Long?,
    storedAuthoritativeTotalBytes: Long?,
): ResumableDownloadDecision {
    if (requestedOffset < 0L) {
        return ResumableDownloadDecision.Reject("Requested download offset cannot be negative")
    }

    if (responseCode == HTTP_OK) {
        if (contentLength == 0L) {
            return ResumableDownloadDecision.Reject(
                "HTTP 200 declared an empty response body for an audio download",
            )
        }
        return ResumableDownloadDecision.Restart(
            totalBytes = contentLength.positiveOrNull(),
        )
    }

    if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && requestedOffset > 0L) {
        return ResumableDownloadDecision.RetryWithoutRange(
            "HTTP 416 rejected the stored partial offset $requestedOffset",
        )
    }

    if (responseCode != HTTP_PARTIAL_CONTENT) {
        return ResumableDownloadDecision.Reject("Unexpected HTTP response $responseCode")
    }

    val range = parseSatisfiedContentRange(contentRange)
        ?: return ResumableDownloadDecision.Reject(
            "Invalid Content-Range for HTTP 206: ${contentRange ?: "missing"}",
        )
    if (range.start != requestedOffset) {
        return ResumableDownloadDecision.Reject(
            "Content-Range starts at ${range.start}, expected $requestedOffset",
        )
    }

    val rangeLength = range.end - range.start + 1L
    if (contentLength != null && contentLength >= 0L && contentLength != rangeLength) {
        return ResumableDownloadDecision.Reject(
            "Content-Length $contentLength does not match Content-Range length $rangeLength",
        )
    }
    val storedTotal = storedAuthoritativeTotalBytes.positiveOrNull()
    if (storedTotal != null && range.total != storedTotal) {
        return ResumableDownloadDecision.RetryWithoutRange(
            "Content-Range total ${range.total} does not match stored response size $storedTotal",
        )
    }

    return ResumableDownloadDecision.Append(
        offset = requestedOffset,
        totalBytes = range.total,
    )
}

internal fun selectResumableEntityValidator(
    etag: String?,
    lastModified: String?,
): ResumableEntityValidator? =
    etag.validStrongEtag()?.let {
        ResumableEntityValidator(ResumableValidatorKind.ETag, it)
    } ?: lastModified.validValidatorValue()?.let {
        ResumableEntityValidator(ResumableValidatorKind.LastModified, it)
    }

internal fun hasResumableEntityValidatorConflict(
    storedValidator: ResumableEntityValidator,
    responseEtag: String?,
    responseLastModified: String?,
): Boolean {
    val responseValue = when (storedValidator.kind) {
        ResumableValidatorKind.ETag -> responseEtag.validValidatorValue()
        ResumableValidatorKind.LastModified -> responseLastModified.validValidatorValue()
    }
    return responseValue != null && responseValue != storedValidator.value
}

internal fun encodeResumableDownloadMetadata(metadata: ResumableDownloadMetadata): String {
    require(metadata.authoritativeTotalBytes == null || metadata.authoritativeTotalBytes > 0L) {
        "Authoritative total byte count must be positive"
    }
    val validator = metadata.validator
    val validValue = when (validator?.kind) {
        ResumableValidatorKind.ETag -> validator.value.validStrongEtag()
        ResumableValidatorKind.LastModified -> validator.value.validValidatorValue()
        null -> null
    }
    require(validator == null || validValue == validator.value) {
        "Entity validator must be valid for If-Range"
    }
    require(isNormalizedDownloadExtension(metadata.fileExtension)) {
        "Download file extension must be normalized"
    }
    require(isNormalizedRepresentationKey(metadata.representationKey)) {
        "Download representation key must be normalized"
    }
    val kind = when (validator?.kind) {
        ResumableValidatorKind.ETag -> VALIDATOR_KIND_ETAG
        ResumableValidatorKind.LastModified -> VALIDATOR_KIND_LAST_MODIFIED
        null -> VALIDATOR_KIND_NONE
    }
    val validatorValue = validator?.value ?: VALIDATOR_VALUE_NONE
    val totalBytes = metadata.authoritativeTotalBytes?.toString() ?: AUTHORITATIVE_TOTAL_UNKNOWN
    return "$METADATA_FORMAT_VERSION\n$totalBytes\n$kind\n$validatorValue\n" +
        "${metadata.representationKey}\n${metadata.fileExtension}"
}

internal fun decodeResumableDownloadMetadata(value: String): ResumableDownloadMetadata? {
    val parts = value.split('\n')
    if (parts.size != 6 || parts[0] != METADATA_FORMAT_VERSION) return null
    val authoritativeTotalBytes = when (parts[1]) {
        AUTHORITATIVE_TOTAL_UNKNOWN -> null
        else -> parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
    }
    val kind = when (parts[2]) {
        VALIDATOR_KIND_ETAG -> ResumableValidatorKind.ETag
        VALIDATOR_KIND_LAST_MODIFIED -> ResumableValidatorKind.LastModified
        VALIDATOR_KIND_NONE -> null
        else -> return null
    }
    val validator = when (kind) {
        ResumableValidatorKind.ETag -> ResumableEntityValidator(
            kind = kind,
            value = parts[3].validStrongEtag() ?: return null,
        )
        ResumableValidatorKind.LastModified -> ResumableEntityValidator(
            kind = kind,
            value = parts[3].validValidatorValue() ?: return null,
        )
        null -> {
            if (parts[3] != VALIDATOR_VALUE_NONE) return null
            null
        }
    }
    val representationKey = parts[4].takeIf(::isNormalizedRepresentationKey) ?: return null
    val fileExtension = parts[5].takeIf(::isNormalizedDownloadExtension) ?: return null
    return ResumableDownloadMetadata(
        validator = validator,
        authoritativeTotalBytes = authoritativeTotalBytes,
        representationKey = representationKey,
        fileExtension = fileExtension,
    )
}

internal fun shouldResetStoredPartial(
    partialBytes: Long,
    metadata: ResumableDownloadMetadata?,
): Boolean {
    require(partialBytes >= 0L) { "Partial byte count cannot be negative" }
    if (partialBytes == 0L) return false
    metadata ?: return true
    val authoritativeTotal = metadata.authoritativeTotalBytes
        ?: return metadata.validator == null
    if (partialBytes > authoritativeTotal) return true
    return partialBytes < authoritativeTotal && metadata.validator == null
}

internal fun isTrustedCompletePartial(
    partialBytes: Long,
    authoritativeTotalBytes: Long?,
): Boolean {
    require(partialBytes >= 0L) { "Partial byte count cannot be negative" }
    val authoritativeTotal = authoritativeTotalBytes?.takeIf { it > 0L } ?: return false
    return partialBytes == authoritativeTotal
}

internal fun shouldResetStoredPartialForRepresentation(
    metadata: ResumableDownloadMetadata?,
    requestedRepresentationKey: String,
    requestedExtension: String,
): Boolean {
    require(isNormalizedRepresentationKey(requestedRepresentationKey)) {
        "Requested download representation key must be normalized"
    }
    require(isNormalizedDownloadExtension(requestedExtension)) {
        "Requested download extension must be normalized"
    }
    metadata ?: return false
    return metadata.representationKey != requestedRepresentationKey ||
        metadata.fileExtension != requestedExtension
}

internal fun resumablePartialFileName(songId: Long, resumeKey: String): String {
    require(songId > 0L) { "Song ID must be positive" }
    require(resumeKey.isNotBlank()) { "Resume key cannot be blank" }
    require(resumeKey == resumeKey.trim().lowercase()) { "Resume key must be normalized" }
    require(resumeKey.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "Resume key contains unsupported filename characters"
    }
    return "$songId.$resumeKey.part"
}

private data class SatisfiedContentRange(
    val start: Long,
    val end: Long,
    val total: Long,
)

private fun parseSatisfiedContentRange(header: String?): SatisfiedContentRange? {
    val match = CONTENT_RANGE_PATTERN.matchEntire(header?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: return null
    if (start < 0L || end < start || total <= end) return null
    return SatisfiedContentRange(start = start, end = end, total = total)
}

private fun Long?.positiveOrNull(): Long? = this?.takeIf { it > 0L }

private fun String?.validValidatorValue(): String? =
    this?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }

private fun String?.validStrongEtag(): String? =
    validValidatorValue()?.takeUnless { it.trimStart().startsWith("W/", ignoreCase = true) }

private fun isNormalizedDownloadExtension(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= 12 &&
        value == value.lowercase() &&
        value.all { it in 'a'..'z' || it in '0'..'9' }

private fun isNormalizedRepresentationKey(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= 32 &&
        value == value.lowercase() &&
        value.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }

private val CONTENT_RANGE_PATTERN = Regex(
    pattern = "bytes\\s+(\\d+)-(\\d+)/(\\d+)",
    option = RegexOption.IGNORE_CASE,
)

private const val HTTP_OK = 200
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val METADATA_FORMAT_VERSION = "v4"
private const val AUTHORITATIVE_TOTAL_UNKNOWN = "unknown"
private const val VALIDATOR_KIND_ETAG = "etag"
private const val VALIDATOR_KIND_LAST_MODIFIED = "last-modified"
private const val VALIDATOR_KIND_NONE = "none"
private const val VALIDATOR_VALUE_NONE = "-"
