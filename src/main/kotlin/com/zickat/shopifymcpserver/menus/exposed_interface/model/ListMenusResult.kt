package com.zickat.shopifymcpserver.menus.exposed_interface.model

data class ListMenusResult(
    val hadQuery: Boolean,
    val blocks: List<String>,
    val truncated: Boolean,
)
