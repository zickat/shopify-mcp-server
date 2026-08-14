package com.zickat.shopifymcpserver.redirects.spi.shopify

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal object RedirectsGraphQL {

    val CREATE_URL_REDIRECT_MUTATION =
        "mutation CreateUrlRedirect(\$urlRedirect: UrlRedirectInput!) {\n" +
            "      urlRedirectCreate(urlRedirect: \$urlRedirect) {\n" +
            "        urlRedirect { id }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    fun createUrlRedirectVariables(fromPath: String, toPath: String): JsonObject = buildJsonObject {
        put(
            "urlRedirect",
            buildJsonObject {
                put("path", JsonPrimitive(fromPath))
                put("target", JsonPrimitive(toPath))
            },
        )
    }

    fun urlRedirectCreatePayload(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("urlRedirectCreate") as? JsonObject
}
