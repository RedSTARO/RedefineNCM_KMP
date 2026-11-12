package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import kotlin.test.Test
import kotlin.test.assertEquals

class XeapiPlaintextTest {
    @Test
    fun matchesUrlSearchParamsEncodingAndUpstreamFieldOrder() {
        val actual = NcmProtocolCrypto.buildXeapiPlaintext(
            "/api/song/enhance/player/url/v1?existing=a%20b#fragment",
            linkedMapOf("ids" to "[347230]", "level" to "lossless", "keyword" to "网易云 +/~*", "e_r" to "true"),
        ).decodeToString()
        assertEquals(
            """{"queryString":"existing=a%20b&e_r=true","content":"ids=%5B347230%5D&level=lossless&keyword=%E7%BD%91%E6%98%93%E4%BA%91+%2B%2F%7E*"}""",
            actual,
        )
    }

    @Test
    fun nonFormTypeStillEncodesUrlSearchParamsButStoresBase64Body() {
        assertEquals(
            """{"contentType":"application/json; charset=utf-8","method":"PATCH","body":"dmFsdWU9YSUyQmI=","queryString":"e_r=true"}""",
            NcmProtocolCrypto.buildXeapiPlaintext(
                "/api/test", linkedMapOf("value" to "a+b", "e_r" to "false"), "patch", "application/json; charset=utf-8",
            ).decodeToString(),
        )
    }

    @Test
    fun stripsResponseFlagFromBodyAndOmitsEmptyContent() {
        for (data in listOf(null, emptyMap(), mapOf("e_r" to "true"))) {
            assertEquals(
                """{"queryString":"e_r=true"}""",
                NcmProtocolCrypto.buildXeapiPlaintext("/api/test", data).decodeToString(),
            )
        }
    }

    @Test
    fun defaultQueryIsInsertedAfterBodyWhenOriginalUriHadNoQuery() {
        assertEquals(
            """{"content":"empty=&symbols=%21%27%28%29%7E%2F%3F%23%26%3D%25&space=+","queryString":"e_r=true"}""",
            NcmProtocolCrypto.buildXeapiPlaintext(
                "/api/test", linkedMapOf("empty" to "", "symbols" to "!'()~/?#&=%", "space" to " "),
            ).decodeToString(),
        )
    }

    @Test
    fun retainsExistingQueryIncludingAnExistingResponseFlag() {
        assertEquals(
            """{"queryString":"e_r=false&x=1&e_r=true"}""",
            NcmProtocolCrypto.buildXeapiPlaintext("https://interface.music.163.com/api/test?e_r=false&x=1", null).decodeToString(),
        )
    }

    @Test
    fun contentTypeAndMethodDefaultsUseSameFallbackAsUpstream() {
        assertEquals(
            """{"content":"key=value","queryString":"e_r=true"}""",
            NcmProtocolCrypto.buildXeapiPlaintext("/api/test?", mapOf("key" to "value"), "", "").decodeToString(),
        )
        assertEquals(
            """{"method":"GET","content":"key=value","queryString":"e_r=true"}""",
            NcmProtocolCrypto.buildXeapiPlaintext(
                "/api/test", mapOf("key" to "value"), "get", "APPLICATION/X-WWW-FORM-URLENCODED;charset=UTF-8",
            ).decodeToString(),
        )
    }
}
