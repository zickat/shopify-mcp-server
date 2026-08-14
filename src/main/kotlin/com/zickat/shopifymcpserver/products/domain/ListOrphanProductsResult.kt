package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.CollectionRef
import com.zickat.shopifymcpserver.products.domain.models.ProductListingEntry

data class ListOrphanProductsResult(
    val orphans: List<ProductListingEntry>,
    val collectionsTruncated: Boolean,
    val collectionsWithTruncatedProducts: List<CollectionRef>,
    val productsTruncated: Boolean,
)
