package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Shapes the decoded NetEase body, not its transport envelope.
 *
 * [cookies] are the upstream request layer's Set-Cookie values, with Domain removed. QR check
 * deliberately joins whole values with `;`, including their remaining attributes, just as the
 * pinned module does. Credential persistence may subsequently normalize them for a Cookie header.
 * QR key/check and audio match expose HTTP 200; the dispatcher owns that status adjustment.
 */
internal fun shapeNcmEndpointResponse(
    kind: NcmEndpointResponseKind,
    body: JsonElement,
    cookies: List<String>,
): JsonElement = when (kind) {
    NcmEndpointResponseKind.IDENTITY -> body
    NcmEndpointResponseKind.USER_DETAIL -> {
        // The original replaces the serialized response globally, not only one profile key.
        ApiJson.parseToJsonElement(body.toString().replace("avatarImgId_str", "avatarImgIdStr"))
    }
    NcmEndpointResponseKind.LOGIN_STATUS -> {
        if ((body as? JsonObject)?.get("code").let { it as? JsonPrimitive }?.intOrNull == 200) {
            buildJsonObject { put("data", body) }
        } else {
            body
        }
    }
    NcmEndpointResponseKind.LOGIN_QR_KEY -> buildJsonObject {
        put("data", body)
        put("code", 200)
    }
    NcmEndpointResponseKind.LOGIN_QR_CHECK -> buildJsonObject {
        (body as? JsonObject)?.forEach { (key, value) -> put(key, value) }
        put("cookie", cookies.joinToString(";"))
    }
    NcmEndpointResponseKind.AUDIO_MATCH -> buildJsonObject {
        put("code", 200)
        (body as? JsonObject)?.get("data")?.let { put("data", it) }
    }
}
