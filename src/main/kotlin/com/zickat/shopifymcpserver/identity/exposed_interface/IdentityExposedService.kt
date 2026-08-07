package com.zickat.shopifymcpserver.identity.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface IdentityExposedService {
    fun exists(identityId: String): Boolean

    fun resolve(issuer: String, subject: String): Either<UseCaseError, String>
}
