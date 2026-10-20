@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithAddress
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import platform.darwin.dispatch_queue_create
import platform.posix.AF_INET
import platform.posix.sockaddr_in
import kotlin.concurrent.Volatile

/**
 * The route is kept by a path monitor on its own queue and read from there, so the player's
 * "resolve the URL now" point and every reporting observation cost nothing. Reachability for a
 * host name, which this used to ask on the main thread at every track start, waits on a DNS
 * lookup. Until the monitor's first answer, an address-based reachability check, which needs no
 * lookup, answers instead.
 */
actual fun currentNetworkType(): NetworkType = NetworkPath.current()

/** Starts following the route, so that the first track already finds an answer. */
fun startNetworkPathMonitor() {
    NetworkPath.current()
}

private object NetworkPath {
    private const val UNKNOWN = 0
    private const val OTHER = 1
    private const val CELLULAR = 2

    @Volatile
    private var route = UNKNOWN
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val cellular = nw_path_get_status(path) == nw_path_status_satisfied &&
                nw_path_uses_interface_type(path, nw_interface_type_cellular)
            route = if (cellular) CELLULAR else OTHER
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.leejlredstar.redefinencm.network-path", null))
        nw_path_monitor_start(monitor)
    }

    fun current(): NetworkType = when (route) {
        CELLULAR -> NetworkType.CELLULAR
        OTHER -> NetworkType.WIFI
        else -> defaultRouteNetworkType()
    }
}

/** iOS marks a cellular default route with `IsWWAN`. The zero address names the default route. */
private fun defaultRouteNetworkType(): NetworkType = memScoped {
    val zeroAddress = alloc<sockaddr_in>()
    zeroAddress.sin_len = sizeOf<sockaddr_in>().convert()
    zeroAddress.sin_family = AF_INET.convert()
    val reachability = SCNetworkReachabilityCreateWithAddress(null, zeroAddress.ptr.reinterpret())
        ?: return@memScoped NetworkType.WIFI
    try {
        val flags = alloc<SCNetworkReachabilityFlagsVar>()
        if (
            SCNetworkReachabilityGetFlags(reachability, flags.ptr) &&
            flags.value and kSCNetworkReachabilityFlagsReachable != 0u &&
            flags.value and kSCNetworkReachabilityFlagsIsWWAN != 0u
        ) {
            NetworkType.CELLULAR
        } else {
            NetworkType.WIFI
        }
    } finally {
        CFRelease(reachability)
    }
}
