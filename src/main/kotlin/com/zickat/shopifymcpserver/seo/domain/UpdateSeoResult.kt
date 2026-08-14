package com.zickat.shopifymcpserver.seo.domain

enum class UpdateSeoOutcome { UPDATED, NO_OP, NOT_FOUND, FAILED }

data class UpdateSeoResult(
    val outcome: UpdateSeoOutcome,
    val resourceTitle: String? = null,
    val finalMetaTitle: String? = null,
    val finalMetaDescription: String? = null,
    val titleModified: Boolean = false,
    val descriptionModified: Boolean = false,
    val failureDetail: String? = null,
) {
    companion object {
        val NoOp = UpdateSeoResult(UpdateSeoOutcome.NO_OP)
        val NotFound = UpdateSeoResult(UpdateSeoOutcome.NOT_FOUND)
        fun failed(detail: String) = UpdateSeoResult(UpdateSeoOutcome.FAILED, failureDetail = detail)
        fun updated(
            title: String,
            finalMetaTitle: String?,
            finalMetaDescription: String?,
            titleModified: Boolean,
            descriptionModified: Boolean,
        ) = UpdateSeoResult(
            UpdateSeoOutcome.UPDATED,
            resourceTitle = title,
            finalMetaTitle = finalMetaTitle,
            finalMetaDescription = finalMetaDescription,
            titleModified = titleModified,
            descriptionModified = descriptionModified,
        )
    }
}
