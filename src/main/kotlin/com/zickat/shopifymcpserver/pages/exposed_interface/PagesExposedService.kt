package com.zickat.shopifymcpserver.pages.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.CreatePageResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.DeletePageResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.ListPagesResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.exposed_interface.model.TogglePagePublishResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageMetafieldsResult
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface PagesExposedService {
    fun getPageMetafields(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult>
    fun listPages(storeId: String, query: String?): Either<UseCaseError, ListPagesResult>
    fun createPage(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, CreatePageResult>
    fun updatePage(storeId: String, pageId: String, title: String?, body: String?, handle: String?): Either<UseCaseError, UpdatePageResult>
    fun togglePagePublish(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, TogglePagePublishResult>
    fun updatePageMetafields(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, UpdatePageMetafieldsResult>
    fun deletePage(storeId: String, pageId: String): Either<UseCaseError, DeletePageResult>
}
