package com.leejlredstar.redefinencm.kmp.data.api

import kotlin.test.Test
import kotlin.test.assertEquals

/** An https page cannot fetch NetEase's http CDN links; only those hosts are upgraded. */
class NeteaseCdnTest {
    @Test
    fun neteaseCdnLinksAreAskedForOverHttps() {
        assertEquals(
            "https://p1.music.126.net/abc/123.jpg?param=140y140",
            secureNeteaseCdnUrl("http://p1.music.126.net/abc/123.jpg?param=140y140"),
        )
        assertEquals("https://m701.music.126.net/x.mp3", secureNeteaseCdnUrl("HTTP://m701.music.126.net/x.mp3"))
        assertEquals("https://p2.music.126.net/a.jpg", secureNeteaseCdnUrl("https://p2.music.126.net/a.jpg"))
    }

    @Test
    fun everythingElseIsLeftAsItIs() {
        assertEquals("http://192.168.1.2:3000/song/url", secureNeteaseCdnUrl("http://192.168.1.2:3000/song/url"))
        assertEquals("http://music.126.net.example.com/a", secureNeteaseCdnUrl("http://music.126.net.example.com/a"))
        assertEquals("http://p1.music.126.net:8080/a", secureNeteaseCdnUrl("http://p1.music.126.net:8080/a"))
        assertEquals("blob:https://app/1", secureNeteaseCdnUrl("blob:https://app/1"))
        assertEquals("", secureNeteaseCdnUrl(""))
    }
}
