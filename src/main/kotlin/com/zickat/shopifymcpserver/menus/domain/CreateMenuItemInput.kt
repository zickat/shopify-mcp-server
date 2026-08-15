package com.zickat.shopifymcpserver.menus.domain

data class CreateMenuItemInput(
    val title: String,
    val resource_id: String? = null,
    val url: String? = null,
)
