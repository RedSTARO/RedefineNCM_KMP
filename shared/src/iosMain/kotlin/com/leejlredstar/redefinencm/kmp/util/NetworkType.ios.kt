@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

/**
 * iOS marks a cellular route with `IsWWAN`. A reachability query is synchronous and cheap, which
 * fits the player's "resolve the URL now" point; no continuous monitor is needed because the
 * quality is selected when a track starts.
 */
actual fun currentNetworkType(): NetworkType {
    val reachability = SCNetworkReachabilityCreateWithName(null, "music.163.com") ?: return NetworkType.WIFI
    return try {
        memScoped {
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) {
                NetworkType.WIFI
            } else if (
                flags.value and kSCNetworkReachabilityFlagsReachable != 0u &&
                flags.value and kSCNetworkReachabilityFlagsIsWWAN != 0u
            ) {
                NetworkType.CELLULAR
            } else {
                NetworkType.WIFI
            }
        }
    } finally {
        CFRelease(reachability)
    }
}
