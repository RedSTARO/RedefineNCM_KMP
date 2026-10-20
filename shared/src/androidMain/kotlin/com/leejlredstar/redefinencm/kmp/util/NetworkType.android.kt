package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.koin.mp.KoinPlatform

actual fun currentNetworkType(): NetworkType = runCatching {
    val context = KoinPlatform.getKoin().get<Context>()
    val manager = context.getSystemService(ConnectivityManager::class.java)
        ?: return@runCatching NetworkType.WIFI
    val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
    networkTypeFor(
        cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        wifiOrEthernet = capabilities != null &&
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)),
        metered = manager.isActiveNetworkMetered,
    )
}.getOrDefault(NetworkType.WIFI)

/**
 * The transport decides, as on iOS and the web and as the settings word it ("on Wi-Fi", "on mobile
 * data"): a VPN app that leaves itself metered made home Wi-Fi count as mobile data. Only a network
 * that names neither transport, such as a VPN hiding the one beneath it, falls back to whether
 * Android counts it as metered, so a VPN over mobile data never gets the Wi-Fi quality.
 */
internal fun networkTypeFor(cellular: Boolean, wifiOrEthernet: Boolean, metered: Boolean): NetworkType = when {
    cellular -> NetworkType.CELLULAR
    wifiOrEthernet -> NetworkType.WIFI
    metered -> NetworkType.CELLULAR
    else -> NetworkType.WIFI
}
