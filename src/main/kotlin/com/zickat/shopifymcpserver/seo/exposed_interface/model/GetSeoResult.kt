package com.zickat.shopifymcpserver.seo.exposed_interface.model

enum class GetSeoOutcome { FOUND, NOT_FOUND }

data class GetSeoResult(
    val outcome: GetSeoOutcome,
    val title: String? = null,
    val metaTitle: String? = null,
    val metaDescription: String? = null,
) {
    companion object {
        fun found(title: String, metaTitle: String?, metaDescription: String?) =
            GetSeoResult(GetSeoOutcome.FOUND, title, metaTitle, metaDescription)
        val NotFound = GetSeoResult(GetSeoOutcome.NOT_FOUND)
    }
}
