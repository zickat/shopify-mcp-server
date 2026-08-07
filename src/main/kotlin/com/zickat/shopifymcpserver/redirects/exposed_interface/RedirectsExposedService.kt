package com.zickat.shopifymcpserver.redirects.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface RedirectsExposedService {
    fun createRedirect(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome>
}
