package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import com.zickat.shopifymcpserver.products.domain.models.ProductListingEntry
import com.zickat.shopifymcpserver.products.domain.models.RelatedGuidesSource

fun deriveContentStatus(contentStatus: String?, hasSummaryPoints: Boolean): String =
    contentStatus ?: if (hasSummaryPoints) "published" else "untreated"

fun deriveRelatedGuidesSource(rawSource: String?): RelatedGuidesSource =
    if (rawSource == "manual") RelatedGuidesSource.MANUAL else RelatedGuidesSource.AUTO

fun ProductFact.toListingEntry(): ProductListingEntry =
    ProductListingEntry(id, title, handle, status, deriveContentStatus(contentStatusValue, hasSummaryPoints))
