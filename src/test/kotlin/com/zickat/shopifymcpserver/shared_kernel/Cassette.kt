package com.zickat.shopifymcpserver.shared_kernel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

const val SINK_SHOPIFY_ADMIN_GRAPHQL = "shopify_admin_graphql"
const val SINK_GEMINI = "gemini"
const val SINK_IMAGE_DOWNLOAD = "image_download"
const val SINK_STAGED_UPLOAD = "staged_upload"

private val cassetteJson = Json { ignoreUnknownKeys = true }

@Serializable
data class Cassette(
    val tool: String,
    val toolInput: JsonElement,
    val toolOutput: JsonElement,
    val calls: List<CassetteCall>,
) {
    companion object {
        fun fromJson(source: String): Cassette = cassetteJson.decodeFromString(serializer(), source)

        fun fromClasspathResource(resourcePath: String): Cassette {
            val stream = requireNotNull(Cassette::class.java.classLoader.getResourceAsStream(resourcePath)) {
                "no cassette resource found at classpath:$resourcePath"
            }
            return fromJson(stream.bufferedReader().use { it.readText() })
        }
    }
}

@Serializable
data class CassetteCall(
    val sink: String,
    val request: JsonElement,
    val response: JsonElement,
)

@Serializable
data class ShopifyAdminGraphQLRequest(
    val query: String,
    val variables: JsonElement,
)

@Serializable
data class ShopifyAdminGraphQLResponse(
    val status: Int,
    val body: JsonElement,
)
