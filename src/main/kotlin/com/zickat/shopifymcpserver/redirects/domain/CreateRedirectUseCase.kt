package com.zickat.shopifymcpserver.redirects.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.models.RequiredRedirectField
import com.zickat.shopifymcpserver.redirects.domain.repositories.RedirectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class CreateRedirectUseCase(
    private val redirectsRepository: RedirectsRepository,
) {

    fun execute(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome> = when {
        fromPath.isBlank() -> CreateRedirectOutcome.invalidInput(RequiredRedirectField.FROM_PATH).right()
        toPath.isBlank() -> CreateRedirectOutcome.invalidInput(RequiredRedirectField.TO_PATH).right()
        else -> redirectsRepository.create(storeId, fromPath, toPath)
    }
}
