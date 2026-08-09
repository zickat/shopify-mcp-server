package com.zickat.shopifymcpserver.seo.domain.models

enum class SeoMechanism { NATIVE, METAFIELD }

enum class SeoResourceType(
    val toolValue: String,
    val gidType: String,
    val typename: String,
    val mechanism: SeoMechanism,
) {
    PRODUCT("product", "Product", "Product", SeoMechanism.NATIVE),
    COLLECTION("collection", "Collection", "Collection", SeoMechanism.NATIVE),
    ARTICLE("article", "Article", "Article", SeoMechanism.METAFIELD),
    PAGE("page", "Page", "Page", SeoMechanism.METAFIELD),
    ;

    companion object {
        fun fromToolValue(value: String): SeoResourceType? = entries.firstOrNull { it.toolValue == value }
    }
}
