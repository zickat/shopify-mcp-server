package com.zickat.shopifymcpserver.tenancy.spi.mongo

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Serializable
@Document(GrantEntity.COLLECTION_NAME)
data class GrantEntity(
    @Contextual val _id: ObjectId,
    @Contextual val identityId: ObjectId,
    @Contextual val storeId: ObjectId,
    val role: String,
    @Contextual val grantedBy: ObjectId,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    companion object {
        const val COLLECTION_NAME = "grants"

        fun fromDomain(domain: Grant): GrantEntity = GrantEntity(
            _id = ObjectId(domain.id.value),
            identityId = ObjectId(domain.identityId),
            storeId = ObjectId(domain.storeId.value),
            role = domain.role.wireValue,
            grantedBy = ObjectId(domain.grantedBy),
            createdAt = domain.createdAt,
            revokedAt = domain.revokedAt,
        )
    }

    fun toDomain(): Either<UseCaseError, Grant> = GrantRole.fromWireValue(role).map { role ->
        Grant(
            id = GrantId(_id.toHexString()),
            identityId = identityId.toHexString(),
            storeId = StoreId(storeId.toHexString()),
            role = role,
            grantedBy = grantedBy.toHexString(),
            createdAt = createdAt,
            revokedAt = revokedAt,
        )
    }
}
