package com.zickat.shopifymcpserver.identity

import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import kotlin.time.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

class IdentityFixtures {
    private var id = IdentityId(ObjectId().toHexString())
    private var issuer = "https://idp.test/${ObjectId()}"
    private var subject = "sub-${ObjectId()}"
    private var displayName = "Test Operator"
    private var createdAt = Clock.System.now()
    private var revokedAt: Instant? = null

    fun withId(id: IdentityId) = apply { this.id = id }
    fun withIssuer(issuer: String) = apply { this.issuer = issuer }
    fun withSubject(subject: String) = apply { this.subject = subject }
    fun revoked() = apply { this.revokedAt = Clock.System.now() }

    fun build(): Identity = Identity(
        id = id,
        issuer = issuer,
        subject = subject,
        displayName = displayName,
        createdAt = createdAt,
        revokedAt = revokedAt,
    )
}
