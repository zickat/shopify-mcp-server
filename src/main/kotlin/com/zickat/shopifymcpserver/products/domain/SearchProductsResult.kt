package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ProductListingEntry

data class SearchProductsResult(
    val entries: List<ProductListingEntry>,
    val truncated: Boolean,
)
