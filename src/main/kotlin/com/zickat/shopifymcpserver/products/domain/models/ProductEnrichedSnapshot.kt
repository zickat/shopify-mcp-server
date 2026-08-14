package com.zickat.shopifymcpserver.products.domain.models

enum class RelatedGuidesSource { MANUAL, AUTO }

data class ProductSpecEntry(val label: String, val value: String)

data class ProductFaqEntry(val question: String, val answer: String)

data class ProductComplementary(val id: String, val title: String)

data class RelatedGuide(val id: String, val handle: String, val title: String)

data class ProductEnrichedFetch(
    val title: String,
    val descriptionHtml: String,
    val status: String,
    val contentStatusValue: String?,
    val productType: String,
    val tags: List<String>,
    val originalTitle: String?,
    val originalDescriptionHtml: String?,
    val summaryPoints: List<String>,
    val whyRecommend: String,
    val howToUse: String,
    val specs: List<ProductSpecEntry>,
    val faq: List<ProductFaqEntry>,
    val complementaryProducts: List<ProductComplementary>,
    val relatedGuides: List<RelatedGuide>,
    val relatedGuidesSourceRaw: String?,
    val idealFor: List<String>,
)

data class ProductEnrichedSnapshot(
    val title: String,
    val descriptionHtml: String,
    val status: String,
    val pipelineStatus: String,
    val productType: String,
    val tags: List<String>,
    val originalTitle: String?,
    val originalDescriptionHtml: String?,
    val summaryPoints: List<String>,
    val whyRecommend: String,
    val howToUse: String,
    val specs: List<ProductSpecEntry>,
    val faq: List<ProductFaqEntry>,
    val complementaryProducts: List<ProductComplementary>,
    val relatedGuides: List<RelatedGuide>,
    val relatedGuidesSource: RelatedGuidesSource,
    val idealFor: List<String>,
)
