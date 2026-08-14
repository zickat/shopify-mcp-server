package com.zickat.shopifymcpserver.metaobjects.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface MetaobjectsExposedService {
    fun listMetaobjects(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult>
    fun getMetaobject(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult>
    fun createMetaobject(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, CreateMetaobjectResult>
    fun updateMetaobject(storeId: String, metaobjectId: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, UpdateMetaobjectResult>
    fun deleteMetaobject(storeId: String, metaobjectId: String, confirmReferencedDeletion: Boolean): Either<UseCaseError, DeleteMetaobjectResult>
}
