package com.zickat.shopifymcpserver.products.exposed_interface.model

enum class UnpublishResourceOutcome { UNPUBLISHED, NOOP, NOT_FOUND, FAILED }

data class UnpublishResourceResult(
    val outcome: UnpublishResourceOutcome,
    val resourceTitle: String? = null,
    val countBefore: Int? = null,
    val resourceId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun unpublished(title: String, countBefore: Int) =
            UnpublishResourceResult(UnpublishResourceOutcome.UNPUBLISHED, resourceTitle = title, countBefore = countBefore)
        fun noop(title: String) = UnpublishResourceResult(UnpublishResourceOutcome.NOOP, resourceTitle = title, countBefore = 0)
        fun notFound(resourceId: String) = UnpublishResourceResult(UnpublishResourceOutcome.NOT_FOUND, resourceId = resourceId)
        fun failed(detail: String) = UnpublishResourceResult(UnpublishResourceOutcome.FAILED, failureDetail = detail)
    }
}
