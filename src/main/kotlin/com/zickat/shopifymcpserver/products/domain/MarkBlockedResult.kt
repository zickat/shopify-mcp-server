package com.zickat.shopifymcpserver.products.domain

enum class MarkBlockedOutcome { MARKED, FAILED }

data class MarkBlockedResult(
    val outcome: MarkBlockedOutcome,
    val failureDetail: String? = null,
) {
    companion object {
        val Marked = MarkBlockedResult(MarkBlockedOutcome.MARKED)
        fun failed(detail: String) = MarkBlockedResult(MarkBlockedOutcome.FAILED, detail)
    }
}
