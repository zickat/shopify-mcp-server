package com.zickat.shopifymcpserver.seo.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface SeoExposedService {
    fun getSeo(storeId: String, resourceType: String, resourceId: String): Either<UseCaseError, GetSeoResult>
    fun updateSeo(
        storeId: String,
        resourceType: String,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, UpdateSeoResult>
}
