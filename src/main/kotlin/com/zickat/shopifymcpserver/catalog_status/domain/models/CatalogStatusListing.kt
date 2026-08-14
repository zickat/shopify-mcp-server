package com.zickat.shopifymcpserver.catalog_status.domain.models

data class CatalogStatusResourceNode(
    val id: String,
    val title: String,
    val handle: String,
    val contentStatus: String?,
    val summary: String?,
    val secondarySignal: String?,
)

data class CatalogStatusListing(
    val resources: List<CatalogStatusResourceNode>,
    val truncated: Boolean,
)
