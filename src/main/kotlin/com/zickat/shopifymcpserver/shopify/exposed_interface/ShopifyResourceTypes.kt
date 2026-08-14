package com.zickat.shopifymcpserver.shopify.exposed_interface

import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
object ShopifyResourceTypes {

    fun gidTypeFor(resourceType: String): String? = GID_TYPES[resourceType]

    private val GID_TYPES = mapOf(
        "product" to "Product",
        "collection" to "Collection",
        "article" to "Article",
        "page" to "Page",
    )
}
