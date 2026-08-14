package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageMetafieldsResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import org.springframework.stereotype.Component

private const val NAMESPACE = "custom"

@Component
class UpdatePageMetafieldsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, UpdatePageMetafieldsResult> = either {
        val snapshot = fetchPageMetafields(shopifyAdminGateway, storeId, pageId).bind()
            ?: return@either UpdatePageMetafieldsResult.notFound(pageId)

        val writes = metafields.map { ShopifyMetafieldWrite(pageId, NAMESPACE, it.key, it.type, it.value) }
        val userErrors = ShopifyMetafields.set(shopifyAdminGateway, storeId, writes).bind()

        if (userErrors.isNotEmpty()) {
            UpdatePageMetafieldsResult.failed(pageId, ShopifyUserErrors.format(userErrors))
        } else {
            val lines = metafields.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
            UpdatePageMetafieldsResult.updated(
                "Page \"${snapshot.title}\" mise à jour — ${metafields.size} metafield(s) custom.* écrit(s) :\n$lines",
            )
        }
    }
}
