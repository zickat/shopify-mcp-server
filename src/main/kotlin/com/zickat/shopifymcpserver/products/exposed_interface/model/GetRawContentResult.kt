package com.zickat.shopifymcpserver.products.exposed_interface.model

enum class GetRawContentOutcome { FOUND, NOT_FOUND }

data class GetRawContentResult(
    val outcome: GetRawContentOutcome,
    val text: String? = null,
    val productId: String? = null,
) {
    companion object {
        fun found(text: String) = GetRawContentResult(GetRawContentOutcome.FOUND, text = text)
        fun notFound(productId: String) = GetRawContentResult(GetRawContentOutcome.NOT_FOUND, productId = productId)
    }
}
