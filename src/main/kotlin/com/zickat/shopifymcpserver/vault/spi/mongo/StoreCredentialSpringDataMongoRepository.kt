package com.zickat.shopifymcpserver.vault.spi.mongo

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StoreCredentialSpringDataMongoRepository : MongoRepository<StoreCredentialEntity, ObjectId> {
    @Query("{ 'storeId': ?0, 'revokedAt': null }")
    fun findActiveByStoreId(storeId: ObjectId): StoreCredentialEntity?
}
