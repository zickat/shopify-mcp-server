package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

/**
 * `schema.md` §3 — « MongoDB ne garantit pas les clés étrangères » : `grant → identity`,
 * `grant → store`, `grant → grantedBy` sont des invariants applicatifs, couverts ici. Patron
 * `testing.md` : contract interface implémentée par le fake ET le repository Mongo, les mêmes cas
 * s'exécutant sur les deux ([GrantFakeRepositoryTest], [GrantMongoRepositoryTest]).
 */
interface GrantRepositoryReferentialIntegrityTest {
    val repository: GrantRepository

    /** Enregistre une identité existante, utilisable comme `identityId`/`grantedBy`. */
    fun registerExistingIdentity(): String

    /** Enregistre une boutique, existante et éventuellement archivée. */
    fun registerStore(archived: Boolean): StoreId

    @Test
    fun `should reject a grant whose store is archived`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = true)
        val grant = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).build()

        repository.save(grant).isLeft() shouldBe true
    }

    @Test
    fun `should reject a grant whose grantedBy is an orphan identity`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val orphanGrantedBy = ObjectId().toHexString()
        val grant = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(orphanGrantedBy).build()

        repository.save(grant).isLeft() shouldBe true
    }

    @Test
    fun `should reject a grant whose identityId is an orphan identity`() {
        val grantedBy = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val orphanIdentityId = ObjectId().toHexString()
        val grant = GrantFixtures().withIdentityId(orphanIdentityId).withStoreId(storeId).withGrantedBy(grantedBy).build()

        repository.save(grant).isLeft() shouldBe true
    }

    @Test
    fun `should reject a grant whose store does not exist at all`() {
        val identityId = registerExistingIdentity()
        val orphanStoreId = StoreId(ObjectId().toHexString())
        val grant = GrantFixtures().withIdentityId(identityId).withStoreId(orphanStoreId).withGrantedBy(identityId).build()

        repository.save(grant).isLeft() shouldBe true
    }

    @Test
    fun `should accept a grant whose identity, grantedBy and non-archived store all exist`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val grant = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).build()

        repository.save(grant).isRight() shouldBe true
    }
}
