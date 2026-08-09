package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.SeoExposedService
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class SeoExposedServiceImpl(
    private val getSeoUseCase: GetSeoUseCase,
) : SeoExposedService {

    override fun getSeo(storeId: String, resourceType: String, resourceId: String): Either<UseCaseError, GetSeoResult> = either {
        val parsedResourceType = SeoResourceType.fromToolValue(resourceType)
            ?: raise(TechnicalError("seo.resource_type.unexpected", mapOf("value" to resourceType)))
        getSeoUseCase.execute(storeId, parsedResourceType, resourceId).bind()
    }
}
