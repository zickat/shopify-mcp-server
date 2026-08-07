package com.zickat.shopifymcpserver.identity.spi.mongo

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Serializable
@Document(IdentityEntity.COLLECTION_NAME)
data class IdentityEntity(
    @Contextual val _id: ObjectId,
    val issuer: String,
    val subject: String,
    val displayName: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    companion object {
        const val COLLECTION_NAME = "identities"

        fun fromDomain(domain: Identity): IdentityEntity = IdentityEntity(
            _id = ObjectId(domain.id.value),
            issuer = domain.issuer,
            subject = domain.subject,
            displayName = domain.displayName,
            createdAt = domain.createdAt,
            revokedAt = domain.revokedAt,
        )
    }

    fun toDomain(): Either<UseCaseError, Identity> = Identity(
        id = IdentityId(_id.toHexString()),
        issuer = issuer,
        subject = subject,
        displayName = displayName,
        createdAt = createdAt,
        revokedAt = revokedAt,
    ).right()
}
