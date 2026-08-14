package com.zickat.shopifymcpserver.pages.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldsSnapshot
import com.zickat.shopifymcpserver.pages.domain.PageNative
import com.zickat.shopifymcpserver.pages.domain.repositories.PageDeleteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PageListing
import com.zickat.shopifymcpserver.pages.domain.repositories.PageMetafieldsWriteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PagePatch
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.stereotype.Repository

private const val METAFIELD_NAMESPACE = "custom"

@Repository
class PageShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : PageRepository {

    override fun list(storeId: String, query: String?): Either<UseCaseError, PageListing> = either {
        val results = mutableListOf<PageSummary>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", query?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.LIST_PAGES_QUERY, variables).bind()
            val connection = PageGraphQL.pagesConnection(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            results += PageGraphQL.pageSummaries(connection)

            val hasNextPage = PageGraphQL.hasNextPage(connection)
            cursor = if (hasNextPage) PageGraphQL.endCursor(connection) else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        PageListing(results, truncated)
    }

    override fun get(storeId: String, pageId: String): Either<UseCaseError, PageNative?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.FETCH_PAGE_NATIVE_QUERY, variables).bind()
        PageGraphQL.parsePageNative(response)
    }

    override fun create(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, PageWriteOutcome> = either {
        val isPublished = publish ?: false
        val pageInput = buildJsonObject {
            put("title", JsonPrimitive(title))
            put("body", JsonPrimitive(body))
            handle?.let { put("handle", JsonPrimitive(it)) }
            put("isPublished", JsonPrimitive(isPublished))
        }
        val variables = buildJsonObject { put("page", pageInput) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.CREATE_PAGE_MUTATION, variables).bind()
        val payload = PageGraphQL.mutationPayload(response, "pageCreate")
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = PageGraphQL.mutationPage(payload)

        if (userErrors.isNotEmpty() || page == null) {
            PageWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            PageWriteOutcome.Success(PageGraphQL.pageNativeFrom(page, fallbackIsPublished = isPublished))
        }
    }

    override fun update(storeId: String, pageId: String, patch: PagePatch): Either<UseCaseError, PageWriteOutcome> = either {
        val pageInput = buildJsonObject {
            patch.title?.let { put("title", JsonPrimitive(it)) }
            patch.body?.let { put("body", JsonPrimitive(it)) }
            patch.handle?.let { put("handle", JsonPrimitive(it)) }
        }
        val variables = buildJsonObject {
            put("id", JsonPrimitive(pageId))
            put("page", pageInput)
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.UPDATE_PAGE_MUTATION, variables).bind()
        val payload = PageGraphQL.mutationPayload(response, "pageUpdate")
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = PageGraphQL.mutationPage(payload)

        if (userErrors.isNotEmpty() || page == null) {
            PageWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            PageWriteOutcome.Success(PageGraphQL.pageNativeFrom(page))
        }
    }

    override fun setPublished(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, PageWriteOutcome> = either {
        val variables = buildJsonObject {
            put("id", JsonPrimitive(pageId))
            put("page", buildJsonObject { put("isPublished", JsonPrimitive(target)) })
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.TOGGLE_PAGE_PUBLISH_MUTATION, variables).bind()
        val payload = PageGraphQL.mutationPayload(response, "pageUpdate")
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = PageGraphQL.mutationPage(payload)

        if (userErrors.isNotEmpty() || page == null) {
            PageWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            PageWriteOutcome.Success(PageGraphQL.pageNativeFrom(page, fallbackIsPublished = target))
        }
    }

    override fun delete(storeId: String, pageId: String): Either<UseCaseError, PageDeleteOutcome> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.DELETE_PAGE_MUTATION, variables).bind()
        val payload = PageGraphQL.mutationPayload(response, "pageDelete")
        val userErrors = ShopifyUserErrors.parse(payload)
        val deletedId = PageGraphQL.deletedPageId(payload)

        if (userErrors.isNotEmpty() || deletedId == null) {
            PageDeleteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            PageDeleteOutcome.Deleted
        }
    }

    override fun metafields(storeId: String, pageId: String): Either<UseCaseError, PageMetafieldsSnapshot?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PageGraphQL.FETCH_PAGE_METAFIELDS_QUERY, variables).bind()
        val pageNode = PageGraphQL.pageNodeForMetafields(response)

        pageNode?.let {
            val connection = PageGraphQL.metafieldsConnection(it) ?: raise(TechnicalError("shopify.graphql.response.malformed"))
            PageGraphQL.metafieldsSnapshotFrom(it, connection)
        }
    }

    override fun setMetafields(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, PageMetafieldsWriteOutcome> = either {
        val writes = metafields.map { ShopifyMetafieldWrite(pageId, METAFIELD_NAMESPACE, it.key, it.type, it.value) }
        val userErrors = ShopifyMetafields.set(shopifyAdminGateway, storeId, writes).bind()

        if (userErrors.isNotEmpty()) {
            PageMetafieldsWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            PageMetafieldsWriteOutcome.Updated
        }
    }
}
