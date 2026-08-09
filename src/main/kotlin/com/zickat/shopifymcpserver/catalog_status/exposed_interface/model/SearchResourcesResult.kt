package com.zickat.shopifymcpserver.catalog_status.exposed_interface.model

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
