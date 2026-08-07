package com.zickat.shopifymcpserver.tenancy.spi.mongo

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GrantSpringDataMongoRepository : MongoRepository<GrantEntity, ObjectId> {
    @Query("{ 'identityId': ?0, 'storeId': ?1, 'revokedAt': null }")
    fun findActiveByIdentityIdAndStoreId(identityId: ObjectId, storeId: ObjectId): GrantEntity?

    @Query("{ 'identityId': ?0, 'revokedAt': null }")
    fun findAllActiveByIdentityId(identityId: ObjectId): List<GrantEntity>
}
