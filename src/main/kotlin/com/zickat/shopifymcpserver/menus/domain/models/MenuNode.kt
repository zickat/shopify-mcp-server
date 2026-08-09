package com.zickat.shopifymcpserver.menus.domain.models

data class MenuNode(
    val id: String,
    val handle: String,
    val title: String,
    val isDefault: Boolean,
    val items: List<MenuItemNode>,
)
