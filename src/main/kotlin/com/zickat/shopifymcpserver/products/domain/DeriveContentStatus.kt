package com.zickat.shopifymcpserver.products.domain

fun deriveContentStatus(contentStatus: String?, hasSummaryPoints: Boolean): String =
    contentStatus ?: if (hasSummaryPoints) "published" else "untreated"
