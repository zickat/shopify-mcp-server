package com.zickat.shopifymcpserver.products.exposed_interface.model

enum class PublishResourceOutcome { PUBLISHED, NOT_FOUND, FAILED }

data class PublishResourceResult(
    val outcome: PublishResourceOutcome,
    val resourceTitle: String? = null,
    val wasNeverPublished: Boolean = false,
    val resourceId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun published(title: String, wasNeverPublished: Boolean) =
            PublishResourceResult(PublishResourceOutcome.PUBLISHED, resourceTitle = title, wasNeverPublished = wasNeverPublished)
        fun notFound(resourceId: String) = PublishResourceResult(PublishResourceOutcome.NOT_FOUND, resourceId = resourceId)
        fun failed(detail: String) = PublishResourceResult(PublishResourceOutcome.FAILED, failureDetail = detail)
    }
}
