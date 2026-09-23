package com.leejlredstar.redefinencm.kmp.transition

/**
 * A model file the app downloads the first time it needs it instead of shipping it in the
 * package. [sha256] pins the exact bytes, so a mirror can serve this file or fail, nothing else.
 */
class DownloadableModel(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
)

/**
 * The beat model files, served from this repository at a fixed commit: jsDelivr's three CDNs
 * first, GitHub's raw file host last. A commit hash makes every address immutable. To ship a new
 * model, commit it, then point [MODEL_COMMIT], the sizes and the hashes here at it;
 * `BeatModelDownloadsTest` fails until the files in the repository match what is pinned.
 *
 * The browser build does not use these: it serves its own copy of the ONNX file.
 */
object BeatModelDownloads {
    private const val REPOSITORY = "RedSTARO/RedefineNCM_KMP"
    private const val MODEL_COMMIT = "f044fd6bb79d1d77a0f7fccac71c6bddb0f687e3"

    /** Where each file lives in the repository, for the mirrors and for the pinning test. */
    const val ONNX_PATH = "shared/src/beatModel/resources/automix/beat_this_small0_t750.onnx"
    const val LITERT_PATH = "shared/src/beatModel/android/automix/beat_this_small0_t750.tflite"

    private fun mirrors(path: String) = listOf(
        "https://cdn.jsdelivr.net/gh/$REPOSITORY@$MODEL_COMMIT/$path",
        "https://fastly.jsdelivr.net/gh/$REPOSITORY@$MODEL_COMMIT/$path",
        "https://gcore.jsdelivr.net/gh/$REPOSITORY@$MODEL_COMMIT/$path",
        "https://raw.githubusercontent.com/$REPOSITORY/$MODEL_COMMIT/$path",
    )

    /** For ONNX Runtime on the desktop. */
    val onnx = DownloadableModel(
        fileName = "beat_this_small0_t750.onnx",
        sizeBytes = 8_743_255L,
        sha256 = "901ce4084d8992c2078703b6bb98386f839caea149fee5c051934bf0d574575c",
        urls = mirrors(ONNX_PATH),
    )

    /** For LiteRT on Android: the conversion rewritten for LiteRT's GPU backend. */
    val liteRt = DownloadableModel(
        fileName = "beat_this_small0_t750.tflite",
        sizeBytes = 8_900_884L,
        sha256 = "875eefde0781672e95314a8387c89a0bb8689d0c743834e43be742d703d519f8",
        urls = mirrors(LITERT_PATH),
    )
}
