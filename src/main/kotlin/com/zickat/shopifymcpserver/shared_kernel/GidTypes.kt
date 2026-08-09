package com.zickat.shopifymcpserver.shared_kernel

private val GID_TYPE_PATTERN = Regex("^gid://shopify/([A-Za-z]+)/.+$")

fun String.gidResourceType(): String? = GID_TYPE_PATTERN.matchEntire(this)?.groupValues?.get(1)

fun String.isGidOfType(expectedType: String): Boolean = gidResourceType() == expectedType

fun String.isGidOfAnyType(expectedTypes: Collection<String>): Boolean = gidResourceType() in expectedTypes
