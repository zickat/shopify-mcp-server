package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.exposed_interface.PagesExposedService
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.ListPagesResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class PagesExposedServiceImpl(
    private val getPageMetafieldsUseCase: GetPageMetafieldsUseCase,
    private val listPagesUseCase: ListPagesUseCase,
) : PagesExposedService {

    override fun getPageMetafields(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult> =
        getPageMetafieldsUseCase.execute(storeId, pageId, keys)

    override fun listPages(storeId: String, query: String?): Either<UseCaseError, ListPagesResult> =
        listPagesUseCase.execute(storeId, query)
}
