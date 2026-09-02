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
 * Five call sites had built this exact configuration independently. Parsers whose settings
 * genuinely differ — the settings backup, the Web download index, the desktop AMLL seek
 * payload — keep their own instance rather than being widened to match this one.
 */
internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
