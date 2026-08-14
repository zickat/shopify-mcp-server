package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedSnapshot

enum class GetEnrichedContentOutcome { FOUND, NOT_FOUND }

data class GetEnrichedContentResult(
    val outcome: GetEnrichedContentOutcome,
    val snapshot: ProductEnrichedSnapshot? = null,
    val productId: String? = null,
) {
    companion object {
        fun found(snapshot: ProductEnrichedSnapshot) = GetEnrichedContentResult(GetEnrichedContentOutcome.FOUND, snapshot = snapshot)
        fun notFound(productId: String) = GetEnrichedContentResult(GetEnrichedContentOutcome.NOT_FOUND, productId = productId)
    }
}
