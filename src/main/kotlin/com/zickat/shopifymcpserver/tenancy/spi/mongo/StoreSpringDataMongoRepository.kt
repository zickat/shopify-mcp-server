package com.zickat.shopifymcpserver.tenancy.spi.mongo

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StoreSpringDataMongoRepository : MongoRepository<StoreEntity, ObjectId> {
    fun findBySlug(slug: String): StoreEntity?
}
