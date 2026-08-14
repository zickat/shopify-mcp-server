package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.stereotype.Component

@Component
class GetSeoUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, GetSeoResult> = either {
        val fetched = fetchResourceSeo(shopifyAdminGateway, storeId, resourceId, resourceType.mechanism).bind()

        if (fetched == null || fetched.typename != resourceType.typename) {
            GetSeoResult.NotFound
        } else {
            GetSeoResult.found(fetched.title, fetched.metaTitle, fetched.metaDescription)
        }
    }
}
