package com.zickat.shopifymcpserver.menus.domain.models

data class MenuItemNode(
    val id: String,
    val title: String,
    val type: MenuItemType,
    val url: String?,
    val resourceId: String?,
    val tags: List<String>,
    val items: List<MenuItemNode>,
)
