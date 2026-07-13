package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ResumableDownloadTest {
    @Test
    fun matchingPartialContentAppendsAtRequestedOffset() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = "bytes 512-1023/2048",
            contentLength = 512L,
            storedAuthoritativeTotalBytes = 2048L,
        )

        assertEquals(
            ResumableDownloadDecision.Append(offset = 512L, totalBytes = 2048L),
            decision,
        )
    }

    @Test
    fun fullResponseRestartsInsteadOfAppending() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 200,
            contentRange = null,
            contentLength = 2048L,
            storedAuthoritativeTotalBytes = 4096L,
        )

        assertEquals(ResumableDownloadDecision.Restart(totalBytes = 2048L), decision)
    }

    @Test
    fun fullResponseWithoutLengthDoesNotReuseStoredTotal() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 200,
            contentRange = null,
            contentLength = null,
            storedAuthoritativeTotalBytes = 4096L,
        )

        assertEquals(ResumableDownloadDecision.Restart(totalBytes = null), decision)
    }

    @Test
    fun fullResponseDeclaringZeroBytesIsRejected() {
        val decision = decideResumableDownload(
            requestedOffset = 0L,
            responseCode = 200,
            contentRange = null,
            contentLength = 0L,
            storedAuthoritativeTotalBytes = null,
        )

        val rejection = assertIs<ResumableDownloadDecision.Reject>(decision)
        assertTrue(rejection.reason.contains("empty response body"))
    }

    @Test
    fun partialResponseWithoutContentRangeIsRejected() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = null,
            contentLength = 512L,
            storedAuthoritativeTotalBytes = 1024L,
        )

        val rejection = assertIs<ResumableDownloadDecision.Reject>(decision)
        assertTrue(rejection.reason.contains("Content-Range"))
    }

    @Test
    fun partialResponseStartingAtWrongOffsetIsRejected() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = "bytes 0-511/1024",
            contentLength = 512L,
            storedAuthoritativeTotalBytes = 1024L,
        )

        val rejection = assertIs<ResumableDownloadDecision.Reject>(decision)
        assertTrue(rejection.reason.contains("expected 512"))
    }

    @Test
    fun partialResponseWithInconsistentLengthIsRejected() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = "bytes 512-1023/2048",
            contentLength = 511L,
            storedAuthoritativeTotalBytes = 2048L,
        )

        val rejection = assertIs<ResumableDownloadDecision.Reject>(decision)
        assertTrue(rejection.reason.contains("does not match"))
    }

    @Test
    fun partialResponseConflictingWithStoredTotalRetriesWithoutRange() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = "bytes 512-1023/4096",
            contentLength = 512L,
            storedAuthoritativeTotalBytes = 2048L,
        )

        val retry = assertIs<ResumableDownloadDecision.RetryWithoutRange>(decision)
        assertTrue(retry.reason.contains("4096"))
        assertTrue(retry.reason.contains("2048"))
    }

    @Test
    fun partialResponseEstablishesTotalWhenSidecarDidNotKnowIt() {
        val decision = decideResumableDownload(
            requestedOffset = 512L,
            responseCode = 206,
            contentRange = "bytes 512-1023/4096",
            contentLength = 512L,
            storedAuthoritativeTotalBytes = null,
        )

        assertEquals(
            ResumableDownloadDecision.Append(offset = 512L, totalBytes = 4096L),
            decision,
        )
    }

    @Test
    fun entityValidatorPrefersEtagAndRoundTrips() {
        val validator = selectResumableEntityValidator(
            etag = "\"version-2\"",
            lastModified = "Sun, 13 Jul 2026 15:00:00 GMT",
        )

        assertEquals(
            ResumableEntityValidator(
                kind = ResumableValidatorKind.ETag,
                value = "\"version-2\"",
            ),
            validator,
        )
        val metadata = ResumableDownloadMetadata(
            validator = validator,
            authoritativeTotalBytes = 4096L,
            representationKey = "lossless",
            fileExtension = "flac",
        )
        assertEquals(
            metadata,
            decodeResumableDownloadMetadata(encodeResumableDownloadMetadata(metadata)),
        )
    }

    @Test
    fun entityValidatorFallsBackToLastModified() {
        assertEquals(
            ResumableEntityValidator(
                kind = ResumableValidatorKind.LastModified,
                value = "Sun, 13 Jul 2026 15:00:00 GMT",
            ),
            selectResumableEntityValidator(
                etag = null,
                lastModified = "Sun, 13 Jul 2026 15:00:00 GMT",
            ),
        )
    }

    @Test
    fun weakEtagFallsBackToLastModified() {
        assertEquals(
            ResumableEntityValidator(
                kind = ResumableValidatorKind.LastModified,
                value = "Sun, 13 Jul 2026 15:00:00 GMT",
            ),
            selectResumableEntityValidator(
                etag = "W/\"version-2\"",
                lastModified = "Sun, 13 Jul 2026 15:00:00 GMT",
            ),
        )
        assertEquals(
            null,
            selectResumableEntityValidator(
                etag = "W/\"version-2\"",
                lastModified = null,
            ),
        )
    }

    @Test
    fun changedResponseValidatorConflictsWithStoredValidator() {
        assertTrue(
            hasResumableEntityValidatorConflict(
                storedValidator = ResumableEntityValidator(
                    kind = ResumableValidatorKind.ETag,
                    value = "\"version-1\"",
                ),
                responseEtag = "\"version-2\"",
                responseLastModified = null,
            )
        )
        assertTrue(
            hasResumableEntityValidatorConflict(
                storedValidator = ResumableEntityValidator(
                    kind = ResumableValidatorKind.LastModified,
                    value = "Sun, 13 Jul 2026 15:00:00 GMT",
                ),
                responseEtag = "\"version-2\"",
                responseLastModified = "Sun, 13 Jul 2026 16:00:00 GMT",
            )
        )
    }

    @Test
    fun matchingOrAbsentResponseValidatorDoesNotConflict() {
        val storedValidator = ResumableEntityValidator(
            kind = ResumableValidatorKind.ETag,
            value = "\"version-1\"",
        )

        assertFalse(
            hasResumableEntityValidatorConflict(
                storedValidator = storedValidator,
                responseEtag = "\"version-1\"",
                responseLastModified = "Sun, 13 Jul 2026 16:00:00 GMT",
            )
        )
        assertFalse(
            hasResumableEntityValidatorConflict(
                storedValidator = storedValidator,
                responseEtag = null,
                responseLastModified = "Sun, 13 Jul 2026 16:00:00 GMT",
            )
        )
    }

    @Test
    fun malformedOrLegacyMetadataSidecarIsRejected() {
        assertEquals(null, decodeResumableDownloadMetadata("legacy-validator-without-format"))
        assertEquals(null, decodeResumableDownloadMetadata("v1\netag\n\"version-1\""))
        assertEquals(
            null,
            decodeResumableDownloadMetadata("v2\n4096\netag\nW/\"version-1\""),
        )
        assertEquals(null, decodeResumableDownloadMetadata("v2\n0\nnone\n-"))
        assertEquals(
            null,
            decodeResumableDownloadMetadata("v3\n4096\netag\n\"version-1\"\n../flac"),
        )
        assertEquals(
            null,
            decodeResumableDownloadMetadata(
                "v4\n4096\netag\n\"version-1\"\nstandard\n../flac",
            ),
        )
    }

    @Test
    fun equalLengthLegacyPartialWithoutValidatorMustRestart() {
        assertTrue(
            shouldResetStoredPartial(
                partialBytes = 2048L,
                metadata = null,
            )
        )
        assertFalse(
            isTrustedCompletePartial(
                partialBytes = 2048L,
                authoritativeTotalBytes = null,
            )
        )
    }

    @Test
    fun metadataWithoutAuthoritativeTotalResumesOnlyWithValidator() {
        val metadata = ResumableDownloadMetadata(
            validator = ResumableEntityValidator(
                kind = ResumableValidatorKind.ETag,
                value = "\"version-1\"",
            ),
            authoritativeTotalBytes = null,
            representationKey = "standard",
            fileExtension = "mp3",
        )

        assertFalse(
            shouldResetStoredPartial(
                partialBytes = 2048L,
                metadata = metadata,
            )
        )
        assertFalse(
            isTrustedCompletePartial(
                partialBytes = 2048L,
                authoritativeTotalBytes = metadata.authoritativeTotalBytes,
            )
        )
        assertEquals(
            metadata,
            decodeResumableDownloadMetadata(encodeResumableDownloadMetadata(metadata)),
        )
    }

    @Test
    fun metadataWithoutTotalOrValidatorMustRestart() {
        assertTrue(
            shouldResetStoredPartial(
                partialBytes = 2048L,
                metadata = ResumableDownloadMetadata(
                    validator = null,
                    authoritativeTotalBytes = null,
                    representationKey = "standard",
                    fileExtension = "mp3",
                ),
            )
        )
    }

    @Test
    fun equalLengthAuthoritativePartialMayCompleteWithoutAnotherRequest() {
        val metadata = ResumableDownloadMetadata(
            validator = ResumableEntityValidator(
                kind = ResumableValidatorKind.ETag,
                value = "\"version-1\"",
            ),
            authoritativeTotalBytes = 2048L,
            representationKey = "standard",
            fileExtension = "mp3",
        )

        assertFalse(
            shouldResetStoredPartial(
                partialBytes = 2048L,
                metadata = metadata,
            )
        )
        assertTrue(
            isTrustedCompletePartial(
                partialBytes = 2048L,
                authoritativeTotalBytes = metadata.authoritativeTotalBytes,
            )
        )
    }

    @Test
    fun interruptionAtStaleExpectedLengthDoesNotCompletePartial() {
        val staleExpectedBytes = 2048L
        val localBytesAtInterruption = staleExpectedBytes
        val metadata = ResumableDownloadMetadata(
            validator = ResumableEntityValidator(
                kind = ResumableValidatorKind.ETag,
                value = "\"version-1\"",
            ),
            authoritativeTotalBytes = 4096L,
            representationKey = "standard",
            fileExtension = "mp3",
        )

        assertFalse(
            isTrustedCompletePartial(
                partialBytes = localBytesAtInterruption,
                authoritativeTotalBytes = metadata.authoritativeTotalBytes,
            )
        )
        assertFalse(
            shouldResetStoredPartial(
                partialBytes = localBytesAtInterruption,
                metadata = metadata,
            )
        )
    }

    @Test
    fun storedPartialRequiresTheSameServerRepresentationAndExtension() {
        val storedMetadata = ResumableDownloadMetadata(
            validator = ResumableEntityValidator(
                kind = ResumableValidatorKind.ETag,
                value = "\"version-1\"",
            ),
            authoritativeTotalBytes = 4096L,
            representationKey = "standard",
            fileExtension = "mp3",
        )

        assertTrue(
            shouldResetStoredPartialForRepresentation(
                metadata = storedMetadata,
                requestedRepresentationKey = "lossless",
                requestedExtension = "flac",
            )
        )
        assertTrue(
            shouldResetStoredPartialForRepresentation(
                metadata = storedMetadata,
                requestedRepresentationKey = "exhigh",
                requestedExtension = "mp3",
            )
        )
        assertFalse(
            shouldResetStoredPartialForRepresentation(
                metadata = storedMetadata,
                requestedRepresentationKey = "standard",
                requestedExtension = "mp3",
            )
        )
    }

    @Test
    fun partialIdentityIgnoresExpectedBytesAndUrlExtensionButSeparatesQuality() {
        val initial = DownloadRequestItem(
            id = 1234L,
            resumeKey = "lossless",
            representationKey = "standard",
            title = "Song",
            artist = "Artist",
            artworkUri = "",
            url = "https://example.test/song.mp3",
            expectedBytes = null,
        )
        val restored = initial.copy(
            url = "https://example.test/song.flac",
            expectedBytes = 2048L,
        )

        assertEquals(
            resumablePartialFileName(initial.id, initial.resumeKey),
            resumablePartialFileName(restored.id, restored.resumeKey),
        )
        assertEquals("1234.lossless.part", resumablePartialFileName(initial.id, initial.resumeKey))
        assertNotEquals(
            resumablePartialFileName(initial.id, initial.resumeKey),
            resumablePartialFileName(initial.id, "exhigh"),
        )
    }

    @Test
    fun unsatisfiedRangeRetriesOnceWithoutStoredPartial() {
        val decision = decideResumableDownload(
            requestedOffset = 2048L,
            responseCode = 416,
            contentRange = "bytes */2048",
            contentLength = 0L,
            storedAuthoritativeTotalBytes = 2048L,
        )

        assertEquals(
            ResumableDownloadDecision.RetryWithoutRange(
                "HTTP 416 rejected the stored partial offset 2048",
            ),
            decision,
        )
    }
}
