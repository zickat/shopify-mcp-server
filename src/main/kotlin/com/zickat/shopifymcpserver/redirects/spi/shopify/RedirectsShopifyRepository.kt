package com.zickat.shopifymcpserver.redirects.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.repositories.RedirectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Repository

@Repository
class RedirectsShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : RedirectsRepository {

    override fun create(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome> = either {
        val variables = RedirectsGraphQL.createUrlRedirectVariables(fromPath, toPath)
        val response = shopifyAdminGateway.executeGraphQL(storeId, RedirectsGraphQL.CREATE_URL_REDIRECT_MUTATION, variables).bind()
        outcomeFrom(response)
    }

    private fun outcomeFrom(response: JsonElement): CreateRedirectOutcome {
        val payload = RedirectsGraphQL.urlRedirectCreatePayload(response)
            ?: return CreateRedirectOutcome.failed("Réponse Shopify inattendue pour urlRedirectCreate.")
        val userErrors = ShopifyUserErrors.parse(payload)
        return when {
            userErrors.isEmpty() -> CreateRedirectOutcome.Created
            userErrors.any { ALREADY_TAKEN.containsMatchIn(it.message) } -> CreateRedirectOutcome.AlreadyExists
            else -> CreateRedirectOutcome.failed(ShopifyUserErrors.format(userErrors))
        }
    }

    companion object {
        private val ALREADY_TAKEN = Regex("path has already been taken", RegexOption.IGNORE_CASE)
    }
}
