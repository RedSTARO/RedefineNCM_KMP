package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings

/**
 * The OPFS scripts run outside Kotlin and cannot reach the copy tables, so they report their own
 * failures as `redefinencm:` codes; this puts a code into words in the language shown. Any other
 * message is the browser's own error text and passes through unchanged.
 */
internal fun webStorageMessage(raw: String): String {
    val code = raw.removePrefix("redefinencm:").takeIf { it != raw } ?: return raw
    return when {
        code == "assets-need-opfs" -> strings.webAssetsNeedOpfs
        code == "invalid-lyric-assets" -> strings.webLyricAssetsInvalid
        code == "invalid-lyric-names" -> strings.webLyricNamesInvalid
        code == "invalid-artwork" -> strings.webArtworkInvalid
        code == "download-needs-opfs" -> strings.webDownloadNeedsOpfs
        code.startsWith("http:") -> strings.downloadHttpFailed(code.removePrefix("http:"))
        code == "download-cancelled" -> strings.downloadCancelled
        code == "invalid-download-uri" -> strings.webDownloadUriInvalid
        code == "invalid-file-name" -> strings.downloadFileNameInvalid
        code == "file-missing" -> strings.webDownloadFileMissing
        else -> raw
    }
}
