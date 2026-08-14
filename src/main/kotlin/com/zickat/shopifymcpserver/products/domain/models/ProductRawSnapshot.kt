package com.zickat.shopifymcpserver.products.domain.models

data class ProductRawSnapshot(
    val title: String,
    val handle: String,
    val status: String,
    val descriptionHtml: String,
    val minPrice: String,
    val maxPrice: String,
    val currency: String,
    val media: List<ProductRawMedia>,
    val options: List<ProductRawOption>,
    val variants: List<ProductRawVariant>,
)

data class ProductRawMedia(
    val id: String,
    val url: String?,
    val altText: String?,
    val variantCombos: List<String>,
)

data class ProductRawOption(
    val name: String,
    val values: List<String>,
)

data class ProductRawVariant(
    val id: String,
    val combo: String,
)
