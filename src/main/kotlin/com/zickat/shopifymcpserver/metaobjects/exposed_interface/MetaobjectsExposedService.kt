package com.zickat.shopifymcpserver.metaobjects.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface MetaobjectsExposedService {
    fun listMetaobjects(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult>
    fun getMetaobject(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult>
}
