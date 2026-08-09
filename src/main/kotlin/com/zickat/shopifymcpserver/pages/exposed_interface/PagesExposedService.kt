package com.zickat.shopifymcpserver.pages.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.ListPagesResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface PagesExposedService {
    fun getPageMetafields(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult>
    fun listPages(storeId: String, query: String?): Either<UseCaseError, ListPagesResult>
}
