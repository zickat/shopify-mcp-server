package com.zickat.shopifymcpserver.pages.domain

data class PageMetafield(
    val key: String,
    val type: String,
    val value: String,
)

data class PageMetafieldsSnapshot(
    val title: String,
    val metafields: List<PageMetafield>,
    val truncated: Boolean,
)
