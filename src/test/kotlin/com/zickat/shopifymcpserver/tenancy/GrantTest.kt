package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.expiresAtViolation
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.junit.jupiter.api.Test

class GrantTest {

    @Test
    fun `isUsable should be true for an active viewer grant with no expiration`() {
        val grant = GrantFixtures().withRole(GrantRole.VIEWER).withExpiresAt(null).build()

        grant.isUsable(Clock.System.now()) shouldBe true
    }

    @Test
    fun `isUsable should be true for an operator grant whose expiration is still ahead`() {
        val grant = GrantFixtures().withRole(GrantRole.OPERATOR).withExpiresAt(Clock.System.now() + 1.hours).build()

        grant.isUsable(Clock.System.now()) shouldBe true
    }

    @Test
    fun `isUsable should be false once now reaches the expiration instant — expiry is inclusive`() {
        val expiresAt = Clock.System.now()
        val grant = GrantFixtures().withRole(GrantRole.OPERATOR).withExpiresAt(expiresAt).build()

        grant.isUsable(expiresAt) shouldBe false
    }

    @Test
    fun `isUsable should be false for an expired operator grant even when it was never revoked`() {
        val grant = GrantFixtures().withRole(GrantRole.OPERATOR).withExpiresAt(Clock.System.now() - 1.hours).build()

        grant.isUsable(Clock.System.now()) shouldBe false
    }

    @Test
    fun `isUsable should be false for a revoked grant regardless of a still-future expiresAt`() {
        val grant = GrantFixtures().withRole(GrantRole.OPERATOR).withExpiresAt(Clock.System.now() + 1.hours).revoked().build()

        grant.isUsable(Clock.System.now()) shouldBe false
    }

    @Test
    fun `expiresAtViolation should require expiresAt for an operator grant`() {
        GrantRole.OPERATOR.expiresAtViolation(null) shouldBe "grant.expires.at.required"
    }

    @Test
    fun `expiresAtViolation should accept an operator grant that carries an expiresAt`() {
        GrantRole.OPERATOR.expiresAtViolation(Clock.System.now() + 1.hours) shouldBe null
    }

    @Test
    fun `expiresAtViolation should forbid expiresAt for a viewer grant`() {
        GrantRole.VIEWER.expiresAtViolation(Clock.System.now() + 1.hours) shouldBe "grant.expires.at.forbidden"
    }

    @Test
    fun `expiresAtViolation should accept a viewer grant with no expiresAt`() {
        GrantRole.VIEWER.expiresAtViolation(null) shouldBe null
    }
}
