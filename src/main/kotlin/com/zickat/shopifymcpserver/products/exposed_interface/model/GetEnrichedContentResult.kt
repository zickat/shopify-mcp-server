package com.zickat.shopifymcpserver.products.exposed_interface.model

enum class GetEnrichedContentOutcome { FOUND, NOT_FOUND }

data class GetEnrichedContentResult(
    val outcome: GetEnrichedContentOutcome,
    val text: String? = null,
    val productId: String? = null,
) {
    companion object {
        fun found(text: String) = GetEnrichedContentResult(GetEnrichedContentOutcome.FOUND, text = text)
        fun notFound(productId: String) = GetEnrichedContentResult(GetEnrichedContentOutcome.NOT_FOUND, productId = productId)
    }
}
