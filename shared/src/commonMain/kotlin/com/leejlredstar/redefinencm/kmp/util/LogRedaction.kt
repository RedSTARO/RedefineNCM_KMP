package com.leejlredstar.redefinencm.kmp.util

private val urlUserInfo = Regex("(https?://)[^\\s/?#]*@", RegexOption.IGNORE_CASE)

/** Redacts URL authority credentials in diagnostic text, keeping the destination and path. */
fun redactUrlUserInfo(message: String): String =
    urlUserInfo.replace(message) { "${it.groupValues[1]}<redacted>@" }
