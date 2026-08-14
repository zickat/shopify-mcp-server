package com.zickat.shopifymcpserver.menus.domain.models

data class MenuListing(
    val menus: List<MenuNode>,
    val truncated: Boolean,
)
