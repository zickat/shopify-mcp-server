package com.zickat.shopifymcpserver.shopify.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Service

@Service
class ShopifyAdminGatewayImpl(
    private val shopifyAdminGraphQLUseCase: ShopifyAdminGraphQLUseCase,
) : ShopifyAdminGateway {

    override fun executeGraphQL(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement> =
        shopifyAdminGraphQLUseCase.executeGraphQL(storeId, query, variables)
}
