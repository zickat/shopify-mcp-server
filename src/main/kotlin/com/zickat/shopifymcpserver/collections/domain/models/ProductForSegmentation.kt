package com.zickat.shopifymcpserver.collections.domain.models

data class ProductForSegmentation(
    val id: String,
    val title: String,
    val status: String,
    val productType: String,
    val tags: List<String>,
)
