package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.MetaobjectsExposedService
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class MetaobjectsExposedServiceImpl(
    private val listMetaobjectsUseCase: ListMetaobjectsUseCase,
    private val getMetaobjectUseCase: GetMetaobjectUseCase,
    private val createMetaobjectUseCase: CreateMetaobjectUseCase,
    private val updateMetaobjectUseCase: UpdateMetaobjectUseCase,
    private val deleteMetaobjectUseCase: DeleteMetaobjectUseCase,
) : MetaobjectsExposedService {

    override fun listMetaobjects(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult> =
        listMetaobjectsUseCase.execute(storeId, type)

    override fun getMetaobject(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult> =
        getMetaobjectUseCase.execute(storeId, metaobjectId)

    override fun createMetaobject(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, CreateMetaobjectResult> =
        createMetaobjectUseCase.execute(storeId, type, fields)

    override fun updateMetaobject(
        storeId: String,
        metaobjectId: String,
        fields: List<MetaobjectFieldInput>,
    ): Either<UseCaseError, UpdateMetaobjectResult> =
        updateMetaobjectUseCase.execute(storeId, metaobjectId, fields)

    override fun deleteMetaobject(
        storeId: String,
        metaobjectId: String,
        confirmReferencedDeletion: Boolean,
    ): Either<UseCaseError, DeleteMetaobjectResult> =
        deleteMetaobjectUseCase.execute(storeId, metaobjectId, confirmReferencedDeletion)
}
