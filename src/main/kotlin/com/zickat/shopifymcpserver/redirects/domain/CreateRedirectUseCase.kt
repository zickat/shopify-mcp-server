package com.zickat.shopifymcpserver.redirects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.RequiredRedirectField
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.stereotype.Component

@Component
class CreateRedirectUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome> = either {
        when {
            fromPath.isBlank() -> CreateRedirectOutcome.invalidInput(RequiredRedirectField.FROM_PATH)
            toPath.isBlank() -> CreateRedirectOutcome.invalidInput(RequiredRedirectField.TO_PATH)
            else -> {
                val variables = buildJsonObject {
                    put(
                        "urlRedirect",
                        buildJsonObject {
                            put("path", JsonPrimitive(fromPath))
                            put("target", JsonPrimitive(toPath))
                        },
                    )
                }
                val response = shopifyAdminGateway.executeGraphQL(storeId, CREATE_URL_REDIRECT_MUTATION, variables).bind()
                outcomeFrom(response)
            }
        }
    }

    private fun outcomeFrom(response: JsonElement): CreateRedirectOutcome {
        val payload = (response as? JsonObject)?.get("urlRedirectCreate") as? JsonObject
            ?: return CreateRedirectOutcome.failed("Réponse Shopify inattendue pour urlRedirectCreate.")
        val userErrors = ShopifyUserErrors.parse(payload)

        if (userErrors.isEmpty()) return CreateRedirectOutcome.Created
        if (userErrors.any { ALREADY_TAKEN.containsMatchIn(it.message) }) return CreateRedirectOutcome.AlreadyExists
        return CreateRedirectOutcome.failed(ShopifyUserErrors.format(userErrors))
    }

    companion object {
        private val ALREADY_TAKEN = Regex("path has already been taken", RegexOption.IGNORE_CASE)

        private const val CREATE_URL_REDIRECT_MUTATION =
            "mutation CreateUrlRedirect(\$urlRedirect: UrlRedirectInput!) {\n" +
                "      urlRedirectCreate(urlRedirect: \$urlRedirect) {\n" +
                "        urlRedirect { id }\n" +
                "        userErrors { field message }\n" +
                "      }\n" +
                "    }"
    }
}
