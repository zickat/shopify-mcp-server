package com.zickat.shopifymcpserver.tenancy.spi.mongo

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Serializable
@Document(StoreEntity.COLLECTION_NAME)
data class StoreEntity(
    @Contextual val _id: ObjectId,
    val slug: String,
    val shopDomain: String,
    val brandProfileRef: String?,
    val createdAt: Instant,
    val archivedAt: Instant?,
) {
    companion object {
        const val COLLECTION_NAME = "stores"

        fun fromDomain(domain: Store): StoreEntity = StoreEntity(
            _id = ObjectId(domain.id.value),
            slug = domain.slug,
            shopDomain = domain.shopDomain,
            brandProfileRef = domain.brandProfileRef,
            createdAt = domain.createdAt,
            archivedAt = domain.archivedAt,
        )
    }

    fun toDomain(): Either<UseCaseError, Store> = Store(
        id = StoreId(_id.toHexString()),
        slug = slug,
        shopDomain = shopDomain,
        brandProfileRef = brandProfileRef,
        createdAt = createdAt,
        archivedAt = archivedAt,
    ).right()
}
