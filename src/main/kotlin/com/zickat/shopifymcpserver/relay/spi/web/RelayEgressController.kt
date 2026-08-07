package com.zickat.shopifymcpserver.relay.spi.web

import com.zickat.shopifymcpserver.shared_kernel.orThrow
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/relay/sinks")
class RelayEgressController(
    private val shopifyAdminGateway: ShopifyAdminGateway,
    private val storeExposedService: StoreExposedService,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @PostMapping(
        "/shopify-admin-graphql",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun shopifyAdminGraphQL(@RequestBody rawBody: String): ResponseEntity<String> {
        val request = json.decodeFromString(ShopifyAdminGraphQLSinkRequestWire.serializer(), rawBody)
        val storeId = storeExposedService.resolveStoreIdBySlug(request.storeId).orThrow()
        val data = shopifyAdminGateway.executeGraphQL(storeId, request.query, request.variables).orThrow()
        val response = ShopifyAdminGraphQLSinkResponseWire(status = 200, body = data)
        return ResponseEntity.ok(json.encodeToString(ShopifyAdminGraphQLSinkResponseWire.serializer(), response))
    }
}

@Serializable
internal data class ShopifyAdminGraphQLSinkRequestWire(
    val storeId: String,
    val query: String,
    val variables: JsonElement,
)

@Serializable
internal data class ShopifyAdminGraphQLSinkResponseWire(
    val status: Int,
    val body: JsonElement,
)
