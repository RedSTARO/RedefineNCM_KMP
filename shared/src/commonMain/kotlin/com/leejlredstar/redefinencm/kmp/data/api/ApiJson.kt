package com.leejlredstar.redefinencm.kmp.data.api

import kotlinx.serialization.json.Json

/**
 * Decoder settings every remote payload in this app is parsed with.
 *
 * `ignoreUnknownKeys` because the NetEase, QQ Music and AMLL-DB responses all carry fields the
 * DTOs do not model and gain more over time; `isLenient` because several routes quote numbers
 * inconsistently; `coerceInputValues` because they send `null` for primitives that the DTOs
 * declare non-null.
 *
 * Use this instance instead of rebuilding the same configuration at a call site. Parsers whose
 * settings differ (the settings backup, the Web download index, the desktop AMLL seek payload)
 * keep their own instance and are not widened to match this one.
 */
internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
