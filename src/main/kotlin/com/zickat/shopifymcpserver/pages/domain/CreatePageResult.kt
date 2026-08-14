package com.zickat.shopifymcpserver.pages.domain

enum class CreatePageOutcome { CREATED, FAILED }

data class CreatePageResult(
    val outcome: CreatePageOutcome,
    val title: String? = null,
    val handle: String? = null,
    val pageId: String? = null,
    val isPublished: Boolean? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun created(page: PageNative) =
            CreatePageResult(CreatePageOutcome.CREATED, title = page.title, handle = page.handle, pageId = page.id, isPublished = page.isPublished)
        fun failed(title: String, detail: String) = CreatePageResult(CreatePageOutcome.FAILED, title = title, failureDetail = detail)
    }
}
