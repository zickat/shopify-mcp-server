package com.zickat.shopifymcpserver.shopify.exposed_interface

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
object ShopifyOnlineStorePublication {

    fun resolveId(gateway: ShopifyAdminGateway, storeId: String): Either<UseCaseError, String> = either {
        val response = gateway.executeGraphQL(storeId, ONLINE_STORE_PUBLICATION_QUERY, JsonObject(emptyMap())).bind()
        val edges = ((response as? JsonObject)?.get("publications") as? JsonObject)?.get("edges") as? JsonArray
        val publicationId = edges?.firstNotNullOfOrNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject
            node?.takeIf { it["name"]?.jsonPrimitive?.contentOrNull == ONLINE_STORE_PUBLICATION_NAME }
                ?.get("id")?.jsonPrimitive?.contentOrNull
        }
        publicationId ?: raise(NotFoundError("shopify.publication.online_store.not_found"))
    }

    private const val ONLINE_STORE_PUBLICATION_NAME = "Online Store"

    private const val ONLINE_STORE_PUBLICATION_QUERY =
        "query OnlineStorePublication { publications(first: 10) { edges { node { id name } } } }"
}
