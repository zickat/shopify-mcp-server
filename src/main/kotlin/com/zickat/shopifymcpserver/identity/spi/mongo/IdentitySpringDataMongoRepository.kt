package com.zickat.shopifymcpserver.identity.spi.mongo

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IdentitySpringDataMongoRepository : MongoRepository<IdentityEntity, ObjectId> {
    @Query("{ 'issuer': ?0, 'subject': ?1 }")
    fun findByIssuerAndSubject(issuer: String, subject: String): IdentityEntity?
}
