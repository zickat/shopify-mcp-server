package com.zickat.shopifymcpserver.redirects

import arrow.core.Either
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.repositories.RedirectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class RedirectsFakeRepository : RedirectsRepository {

    data class CreateCall(val storeId: String, val fromPath: String, val toPath: String)

    var createResponse: Either<UseCaseError, CreateRedirectOutcome>? = null

    val createCalls = mutableListOf<CreateCall>()

    override fun create(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome> {
        createCalls += CreateCall(storeId, fromPath, toPath)
        return requireNotNull(createResponse) { "createResponse must be set before calling create()" }
    }
}
