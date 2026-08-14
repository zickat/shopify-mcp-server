package com.zickat.shopifymcpserver.pages.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldsSnapshot
import com.zickat.shopifymcpserver.pages.domain.PageNative
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

data class PageSummary(
    val id: String,
    val title: String,
    val handle: String,
)

data class PageListing(
    val pages: List<PageSummary>,
    val truncated: Boolean,
)

data class PagePatch(
    val title: String?,
    val body: String?,
    val handle: String?,
)

sealed interface PageWriteOutcome {
    data class Success(val page: PageNative) : PageWriteOutcome
    data class Failed(val detail: String) : PageWriteOutcome
}

sealed interface PageDeleteOutcome {
    data object Deleted : PageDeleteOutcome
    data class Failed(val detail: String) : PageDeleteOutcome
}

sealed interface PageMetafieldsWriteOutcome {
    data object Updated : PageMetafieldsWriteOutcome
    data class Failed(val detail: String) : PageMetafieldsWriteOutcome
}

interface PageRepository {
    fun list(storeId: String, query: String?): Either<UseCaseError, PageListing>
    fun get(storeId: String, pageId: String): Either<UseCaseError, PageNative?>
    fun create(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, PageWriteOutcome>
    fun update(storeId: String, pageId: String, patch: PagePatch): Either<UseCaseError, PageWriteOutcome>
    fun setPublished(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, PageWriteOutcome>
    fun delete(storeId: String, pageId: String): Either<UseCaseError, PageDeleteOutcome>
    fun metafields(storeId: String, pageId: String): Either<UseCaseError, PageMetafieldsSnapshot?>
    fun setMetafields(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, PageMetafieldsWriteOutcome>
}
