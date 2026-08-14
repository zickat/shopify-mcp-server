package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.SeoExposedService
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class SeoExposedServiceImpl(
    private val getSeoUseCase: GetSeoUseCase,
    private val updateSeoUseCase: UpdateSeoUseCase,
) : SeoExposedService {

    override fun getSeo(storeId: String, resourceType: String, resourceId: String): Either<UseCaseError, GetSeoResult> = either {
        val parsedResourceType = parseResourceType(resourceType).bind()
        getSeoUseCase.execute(storeId, parsedResourceType, resourceId).bind()
    }

    override fun updateSeo(
        storeId: String,
        resourceType: String,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, UpdateSeoResult> = either {
        val parsedResourceType = parseResourceType(resourceType).bind()
        updateSeoUseCase.execute(storeId, parsedResourceType, resourceId, seoTitle, seoDescription).bind()
    }

    private fun parseResourceType(resourceType: String): Either<UseCaseError, SeoResourceType> = either {
        SeoResourceType.fromToolValue(resourceType)
            ?: raise(TechnicalError("seo.resource_type.unexpected", mapOf("value" to resourceType)))
    }
}
