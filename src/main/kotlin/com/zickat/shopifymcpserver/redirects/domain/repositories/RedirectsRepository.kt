package com.zickat.shopifymcpserver.redirects.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface RedirectsRepository {
    fun create(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome>
}
