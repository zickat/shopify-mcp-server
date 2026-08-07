package com.zickat.shopifymcpserver.shopify.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonElement
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface ShopifyAdminGateway {
    fun executeGraphQL(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement>
}
