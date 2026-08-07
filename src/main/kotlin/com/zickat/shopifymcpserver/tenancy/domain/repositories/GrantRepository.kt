package com.zickat.shopifymcpserver.tenancy.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId

interface GrantRepository {
    /**
     * Persiste un grant. Doit échouer (`Either.Left`) si `identityId`/`grantedBy` ne référencent
     * aucune identité existante, si `storeId` référence une boutique archivée ou inexistante, ou
     * si un grant actif existe déjà pour `(identityId, storeId)` — cette dernière contrainte est
     * en plus garantie par l'index unique partiel au niveau du moteur (`schema.md` §3).
     */
    fun save(grant: Grant): Either<UseCaseError, Grant>
    fun findById(id: GrantId): Either<UseCaseError, Grant>

    /** Chemin chaud : lu à chaque appel d'outil (`schema.md` §3, index `(identityId, storeId, revokedAt)`). */
    fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant>
}
