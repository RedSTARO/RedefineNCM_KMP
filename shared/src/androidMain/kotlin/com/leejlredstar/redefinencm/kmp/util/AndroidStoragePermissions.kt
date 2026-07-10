package com.leejlredstar.redefinencm.kmp.util

/** Public Downloads requires WRITE_EXTERNAL_STORAGE only through Android 9 (API 28). */
fun requiresLegacyDownloadWritePermission(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt <= 28 && !permissionGranted
