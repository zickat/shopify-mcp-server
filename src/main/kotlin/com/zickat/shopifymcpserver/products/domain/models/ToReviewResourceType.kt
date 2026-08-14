package com.zickat.shopifymcpserver.products.domain.models

enum class ToReviewResourceType(val toolValue: String, val pluralField: String) {
    PRODUCT("product", "products"),
    COLLECTION("collection", "collections"),
    ARTICLE("article", "articles"),
}

data class ToReviewEntry(
    val id: String,
    val title: String,
    val handle: String,
)
