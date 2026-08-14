package com.zickat.shopifymcpserver.pages

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldsSnapshot
import com.zickat.shopifymcpserver.pages.domain.PageNative
import com.zickat.shopifymcpserver.pages.domain.repositories.PageDeleteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PageListing
import com.zickat.shopifymcpserver.pages.domain.repositories.PageMetafieldsWriteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PagePatch
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class PageFakeRepository : PageRepository {

    data class ListCall(val storeId: String, val query: String?)
    data class UpdateCall(val storeId: String, val pageId: String, val patch: PagePatch)
    data class SetPublishedCall(val storeId: String, val pageId: String, val target: Boolean)
    data class DeleteCall(val storeId: String, val pageId: String)
    data class SetMetafieldsCall(val storeId: String, val pageId: String, val metafields: List<PageMetafieldInput>)

    var listing: Either<UseCaseError, PageListing> = PageListing(emptyList(), truncated = false).right()
    var byId = mutableMapOf<String, PageNative>()
    var metafieldsById = mutableMapOf<String, PageMetafieldsSnapshot>()

    var createResponse: Either<UseCaseError, PageWriteOutcome>? = null
    var updateResponse: Either<UseCaseError, PageWriteOutcome>? = null
    var setPublishedResponse: Either<UseCaseError, PageWriteOutcome>? = null
    var deleteResponse: Either<UseCaseError, PageDeleteOutcome>? = null
    var setMetafieldsResponse: Either<UseCaseError, PageMetafieldsWriteOutcome>? = null

    val listCalls = mutableListOf<ListCall>()
    val updateCalls = mutableListOf<UpdateCall>()
    val setPublishedCalls = mutableListOf<SetPublishedCall>()
    val deleteCalls = mutableListOf<DeleteCall>()
    val setMetafieldsCalls = mutableListOf<SetMetafieldsCall>()

    override fun list(storeId: String, query: String?): Either<UseCaseError, PageListing> {
        listCalls += ListCall(storeId, query)
        return listing
    }

    override fun get(storeId: String, pageId: String): Either<UseCaseError, PageNative?> = byId[pageId].right()

    override fun create(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, PageWriteOutcome> =
        requireNotNull(createResponse) { "createResponse must be set before calling create()" }

    override fun update(storeId: String, pageId: String, patch: PagePatch): Either<UseCaseError, PageWriteOutcome> {
        updateCalls += UpdateCall(storeId, pageId, patch)
        return requireNotNull(updateResponse) { "updateResponse must be set before calling update()" }
    }

    override fun setPublished(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, PageWriteOutcome> {
        setPublishedCalls += SetPublishedCall(storeId, pageId, target)
        return requireNotNull(setPublishedResponse) { "setPublishedResponse must be set before calling setPublished()" }
    }

    override fun delete(storeId: String, pageId: String): Either<UseCaseError, PageDeleteOutcome> {
        deleteCalls += DeleteCall(storeId, pageId)
        return requireNotNull(deleteResponse) { "deleteResponse must be set before calling delete()" }
    }

    override fun metafields(storeId: String, pageId: String): Either<UseCaseError, PageMetafieldsSnapshot?> = metafieldsById[pageId].right()

    override fun setMetafields(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, PageMetafieldsWriteOutcome> {
        setMetafieldsCalls += SetMetafieldsCall(storeId, pageId, metafields)
        return requireNotNull(setMetafieldsResponse) { "setMetafieldsResponse must be set before calling setMetafields()" }
    }
}
