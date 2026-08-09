package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.MetaobjectsExposedService
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class MetaobjectsExposedServiceImpl(
    private val listMetaobjectsUseCase: ListMetaobjectsUseCase,
    private val getMetaobjectUseCase: GetMetaobjectUseCase,
) : MetaobjectsExposedService {

    override fun listMetaobjects(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult> =
        listMetaobjectsUseCase.execute(storeId, type)

    override fun getMetaobject(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult> =
        getMetaobjectUseCase.execute(storeId, metaobjectId)
}
