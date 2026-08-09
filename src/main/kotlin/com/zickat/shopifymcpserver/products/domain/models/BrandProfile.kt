package com.zickat.shopifymcpserver.products.domain.models

data class BrandProfile(
    val storeId: String,
    val name: String,
    val tone: BrandProfileTone,
    val localization: BrandProfileLocalization,
    val design: BrandProfileDesign,
    val content: BrandProfileContent,
)

data class BrandProfileTone(
    val voice: String,
    val forbiddenTopics: List<String>,
    val blockedOriginTerms: List<String> = emptyList(),
)

data class BrandProfileLocalization(
    val convertSupplierSizesToFr: Boolean,
    val applicableCategories: List<String>,
)

data class BrandProfileDesign(
    val primaryColor: String?,
    val secondaryColor: String? = null,
)

data class BrandProfileContent(
    val articleAuthorName: String,
)
