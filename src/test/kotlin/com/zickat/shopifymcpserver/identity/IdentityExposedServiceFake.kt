package com.zickat.shopifymcpserver.identity

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.bson.types.ObjectId

class IdentityExposedServiceFake : IdentityExposedService {
    val existingIds = mutableSetOf<String>()
    private val byIssuerSubject = mutableMapOf<Pair<String, String>, String>()

    override fun exists(identityId: String): Boolean = identityId in existingIds

    override fun resolve(issuer: String, subject: String): Either<UseCaseError, String> {
        val id = byIssuerSubject.getOrPut(issuer to subject) {
            ObjectId().toHexString().also { existingIds.add(it) }
        }
        return id.right()
    }
}
