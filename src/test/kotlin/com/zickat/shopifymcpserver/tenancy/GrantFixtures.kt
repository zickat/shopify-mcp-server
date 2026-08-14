package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

class GrantFixtures {
    private var id = GrantId(ObjectId().toHexString())
    private var identityId = ObjectId().toHexString()
    private var storeId = StoreId(ObjectId().toHexString())
    private var role = GrantRole.VIEWER
    private var grantedBy = ObjectId().toHexString()
    private var createdAt = Clock.System.now()
    private var expiresAt: Instant? = null
    private var expiresAtOverridden = false
    private var revokedAt: Instant? = null

    fun withId(id: GrantId) = apply { this.id = id }
    fun withIdentityId(identityId: String) = apply { this.identityId = identityId }
    fun withStoreId(storeId: StoreId) = apply { this.storeId = storeId }
    fun withRole(role: GrantRole) = apply { this.role = role }
    fun withGrantedBy(grantedBy: String) = apply { this.grantedBy = grantedBy }
    fun withExpiresAt(expiresAt: Instant?) = apply { this.expiresAt = expiresAt; this.expiresAtOverridden = true }
    fun revoked() = apply { this.revokedAt = Clock.System.now() }

    fun build(): Grant = Grant(
        id = id,
        identityId = identityId,
        storeId = storeId,
        role = role,
        grantedBy = grantedBy,
        createdAt = createdAt,
        expiresAt = if (expiresAtOverridden) expiresAt else defaultExpiresAtFor(role),
        revokedAt = revokedAt,
    )

    private fun defaultExpiresAtFor(role: GrantRole): Instant? = when (role) {
        GrantRole.OPERATOR -> createdAt + 1.hours
        GrantRole.VIEWER -> null
    }
}
