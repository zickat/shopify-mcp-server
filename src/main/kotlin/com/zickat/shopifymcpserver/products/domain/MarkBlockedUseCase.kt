package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.MarkBlockedResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import org.springframework.stereotype.Component

@Component
class MarkBlockedUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, MarkBlockedResult> = either {
        val userErrors = ShopifyMetafields.set(
            shopifyAdminGateway,
            storeId,
            listOf(
                ShopifyMetafieldWrite(
                    ownerId = resourceId,
                    namespace = CONTENT_STATUS_NAMESPACE,
                    key = CONTENT_STATUS_KEY,
                    type = "single_line_text_field",
                    value = "blocked",
                ),
            ),
        ).bind()

        if (userErrors.isEmpty()) MarkBlockedResult.Marked else MarkBlockedResult.failed(ShopifyUserErrors.format(userErrors))
    }

    companion object {
        const val CONTENT_STATUS_NAMESPACE = "custom"
        const val CONTENT_STATUS_KEY = "content_status"
    }
}
