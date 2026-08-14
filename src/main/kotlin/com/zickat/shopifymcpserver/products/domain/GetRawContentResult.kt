package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot

enum class GetRawContentOutcome { FOUND, NOT_FOUND }

data class GetRawContentResult(
    val outcome: GetRawContentOutcome,
    val snapshot: ProductRawSnapshot? = null,
    val productId: String? = null,
) {
    companion object {
        fun found(snapshot: ProductRawSnapshot) = GetRawContentResult(GetRawContentOutcome.FOUND, snapshot = snapshot)
        fun notFound(productId: String) = GetRawContentResult(GetRawContentOutcome.NOT_FOUND, productId = productId)
    }
}
