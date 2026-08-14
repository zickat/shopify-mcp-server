package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.exposed_interface.PagesExposedService
import com.zickat.shopifymcpserver.pages.exposed_interface.model.CreatePageResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.DeletePageResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.ListPagesResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.exposed_interface.model.TogglePagePublishResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class PagesExposedServiceImpl(
    private val getPageMetafieldsUseCase: GetPageMetafieldsUseCase,
    private val listPagesUseCase: ListPagesUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val updatePageUseCase: UpdatePageUseCase,
    private val togglePagePublishUseCase: TogglePagePublishUseCase,
    private val updatePageMetafieldsUseCase: UpdatePageMetafieldsUseCase,
    private val deletePageUseCase: DeletePageUseCase,
) : PagesExposedService {

    override fun getPageMetafields(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult> =
        getPageMetafieldsUseCase.execute(storeId, pageId, keys)

    override fun listPages(storeId: String, query: String?): Either<UseCaseError, ListPagesResult> =
        listPagesUseCase.execute(storeId, query)

    override fun createPage(
        storeId: String,
        title: String,
        body: String,
        handle: String?,
        publish: Boolean?,
    ): Either<UseCaseError, CreatePageResult> =
        createPageUseCase.execute(storeId, title, body, handle, publish)

    override fun updatePage(
        storeId: String,
        pageId: String,
        title: String?,
        body: String?,
        handle: String?,
    ): Either<UseCaseError, UpdatePageResult> =
        updatePageUseCase.execute(storeId, pageId, title, body, handle)

    override fun togglePagePublish(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, TogglePagePublishResult> =
        togglePagePublishUseCase.execute(storeId, pageId, target)

    override fun updatePageMetafields(
        storeId: String,
        pageId: String,
        metafields: List<PageMetafieldInput>,
    ): Either<UseCaseError, UpdatePageMetafieldsResult> =
        updatePageMetafieldsUseCase.execute(storeId, pageId, metafields)

    override fun deletePage(storeId: String, pageId: String): Either<UseCaseError, DeletePageResult> =
        deletePageUseCase.execute(storeId, pageId)
}
