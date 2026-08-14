package com.zickat.shopifymcpserver.menus.domain.models

data class ListMenusResult(
    val hadQuery: Boolean,
    val blocks: List<String>,
    val truncated: Boolean,
)
