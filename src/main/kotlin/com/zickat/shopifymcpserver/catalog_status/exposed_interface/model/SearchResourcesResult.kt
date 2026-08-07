package com.zickat.shopifymcpserver.catalog_status.exposed_interface.model

const val MAX_SEARCH_PAGES = 10

data class ResourceSummary(
    val id: String,
    val title: String,
    val handle: String,
    val contentStatus: String,
)

data class SearchResourcesResult(
    val resourceType: SearchResourceType,
    val resources: List<ResourceSummary>,
    val truncated: Boolean,
)
