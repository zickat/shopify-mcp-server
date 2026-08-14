package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class CreatePageOutcome { CREATED, FAILED }

data class CreatePageResult(
    val outcome: CreatePageOutcome,
    val text: String? = null,
    val title: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun created(text: String) = CreatePageResult(CreatePageOutcome.CREATED, text = text)
        fun failed(title: String, detail: String) = CreatePageResult(CreatePageOutcome.FAILED, title = title, failureDetail = detail)
    }
}
