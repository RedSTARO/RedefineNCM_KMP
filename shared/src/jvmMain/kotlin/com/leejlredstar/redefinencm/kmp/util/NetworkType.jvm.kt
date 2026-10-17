package com.leejlredstar.redefinencm.kmp.util

/** Desktop hosts have no mobile-data transport the app can switch between. */
actual fun currentNetworkType(): NetworkType = NetworkType.WIFI
