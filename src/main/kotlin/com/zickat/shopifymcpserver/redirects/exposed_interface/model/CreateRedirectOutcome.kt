package com.zickat.shopifymcpserver.redirects.exposed_interface.model

enum class CreateRedirectStatus { CREATED, ALREADY_EXISTS, FAILED, INVALID_INPUT }

enum class RequiredRedirectField { FROM_PATH, TO_PATH }

data class CreateRedirectOutcome(
    val status: CreateRedirectStatus,
    val failureDetail: String? = null,
    val invalidField: RequiredRedirectField? = null,
) {
    companion object {
        val Created = CreateRedirectOutcome(CreateRedirectStatus.CREATED)
        val AlreadyExists = CreateRedirectOutcome(CreateRedirectStatus.ALREADY_EXISTS)
        fun failed(detail: String) = CreateRedirectOutcome(CreateRedirectStatus.FAILED, failureDetail = detail)
        fun invalidInput(field: RequiredRedirectField) = CreateRedirectOutcome(CreateRedirectStatus.INVALID_INPUT, invalidField = field)
    }
}
