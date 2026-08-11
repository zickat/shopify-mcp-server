package com.zickat.shopifymcpserver.products.domain

private val TAG_PATTERN = Regex("<[^>]+>")
private val WHITESPACE_PATTERN = Regex("\\s+")

fun stripHtml(html: String): String =
    html
        .replace(TAG_PATTERN, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
