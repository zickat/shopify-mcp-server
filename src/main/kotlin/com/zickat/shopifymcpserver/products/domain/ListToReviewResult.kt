package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType

data class ListToReviewResult(
    val resourceType: ToReviewResourceType,
    val entries: List<ToReviewEntry>,
)
