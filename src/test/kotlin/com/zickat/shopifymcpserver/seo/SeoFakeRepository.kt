package com.zickat.shopifymcpserver.seo

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoRepository
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class SeoFakeRepository : SeoRepository {

    data class GetCall(val storeId: String, val resourceType: SeoResourceType, val resourceId: String)
    data class UpdateCall(val storeId: String, val resourceType: SeoResourceType, val resourceId: String, val seoTitle: String?, val seoDescription: String?)

    var getResponse: Either<UseCaseError, SeoSnapshot?> = null.right()
    var updateResponse: Either<UseCaseError, SeoWriteOutcome>? = null

    val getCalls = mutableListOf<GetCall>()
    val updateCalls = mutableListOf<UpdateCall>()

    override fun get(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, SeoSnapshot?> {
        getCalls += GetCall(storeId, resourceType, resourceId)
        return getResponse
    }

    override fun update(
        storeId: String,
        resourceType: SeoResourceType,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, SeoWriteOutcome> {
        updateCalls += UpdateCall(storeId, resourceType, resourceId, seoTitle, seoDescription)
        return requireNotNull(updateResponse) { "updateResponse must be set before calling update()" }
    }
}
