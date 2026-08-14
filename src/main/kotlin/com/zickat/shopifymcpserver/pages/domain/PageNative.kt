package com.zickat.shopifymcpserver.pages.domain

data class PageNative(
    val id: String,
    val title: String,
    val handle: String,
    val isPublished: Boolean,
    val body: String,
)
