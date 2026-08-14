package com.zickat.shopifymcpserver.products.domain.models

data class ProductFact(
    val id: String,
    val title: String,
    val handle: String,
    val status: String,
    val contentStatusValue: String?,
    val hasSummaryPoints: Boolean,
)

data class ProductScan(
    val entries: List<ProductFact>,
    val truncated: Boolean,
)

data class CollectionRef(
    val id: String,
    val title: String,
)

data class OrphanScan(
    val orphans: List<ProductFact>,
    val collectionsTruncated: Boolean,
    val collectionsWithTruncatedProducts: List<CollectionRef>,
    val productsTruncated: Boolean,
)

data class ProductListingEntry(
    val id: String,
    val title: String,
    val handle: String,
    val status: String,
    val contentStatus: String,
)
