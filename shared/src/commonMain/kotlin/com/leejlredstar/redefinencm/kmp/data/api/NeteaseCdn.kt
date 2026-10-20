package com.leejlredstar.redefinencm.kmp.data.api

/**
 * NetEase hands out cover and audio links on its own CDN (`*.music.126.net`) as http:// about as
 * often as https://, and the CDN answers both. A page served over https cannot fetch an http link
 * (mixed content), so such links are asked for over https. Only NetEase's CDN hosts are touched,
 * and only without an explicit port: the user's own server stays exactly as configured.
 */
fun secureNeteaseCdnUrl(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true)) return url
    val rest = url.substring("http://".length)
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    if (':' in authority || '@' in authority) return url
    return if (authority.lowercase().endsWith(".music.126.net")) "https://$rest" else url
}
