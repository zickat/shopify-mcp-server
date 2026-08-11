package com.zickat.shopifymcpserver.products.exposed_interface.model

enum class ToReviewResourceType(val toolValue: String, val pluralField: String) {
    PRODUCT("product", "products"),
    COLLECTION("collection", "collections"),
    ARTICLE("article", "articles"),
}
