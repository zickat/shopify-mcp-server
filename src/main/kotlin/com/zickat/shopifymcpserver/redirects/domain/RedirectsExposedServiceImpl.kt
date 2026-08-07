package com.zickat.shopifymcpserver.redirects.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.redirects.exposed_interface.RedirectsExposedService
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class RedirectsExposedServiceImpl(
    private val createRedirectUseCase: CreateRedirectUseCase,
) : RedirectsExposedService {

    override fun createRedirect(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome> =
        createRedirectUseCase.execute(storeId, fromPath, toPath)
}
