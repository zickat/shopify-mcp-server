package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.domain.ActiveStoreSelectionRegistry
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

class ActiveStoreSelectionRegistryTest {

    private val identityId = ObjectId().toHexString()
    private val otherIdentityId = ObjectId().toHexString()
    private val velotrip = StoreId(ObjectId().toHexString())
    private val lurelab = StoreId(ObjectId().toHexString())

    @Test
    fun `should return no active store for a session that never selected one`() {
        val registry = ActiveStoreSelectionRegistry()

        registry.activeStoreFor(identityId, "session-1") shouldBe null
    }

    @Test
    fun `should return the selected store for the session that selected it`() {
        val registry = ActiveStoreSelectionRegistry()

        registry.select(identityId, "session-1", velotrip)

        registry.activeStoreFor(identityId, "session-1") shouldBe velotrip
    }

    @Test
    fun `two sessions of the same identity never share their active store — a use_store in one never repoints the other`() {
        val registry = ActiveStoreSelectionRegistry()

        registry.select(identityId, "session-desktop", velotrip)
        registry.select(identityId, "session-code", lurelab)

        registry.activeStoreFor(identityId, "session-desktop") shouldBe velotrip
        registry.activeStoreFor(identityId, "session-code") shouldBe lurelab
    }

    @Test
    fun `the same sessionId used by two different identities keeps their selections independent`() {
        val registry = ActiveStoreSelectionRegistry()

        registry.select(identityId, "shared-session-id", velotrip)
        registry.select(otherIdentityId, "shared-session-id", lurelab)

        registry.activeStoreFor(identityId, "shared-session-id") shouldBe velotrip
        registry.activeStoreFor(otherIdentityId, "shared-session-id") shouldBe lurelab
    }

    @Test
    fun `a later selection in the same session overrides the earlier one, it does not accumulate`() {
        val registry = ActiveStoreSelectionRegistry()

        registry.select(identityId, "session-1", velotrip)
        registry.select(identityId, "session-1", lurelab)

        registry.activeStoreFor(identityId, "session-1") shouldBe lurelab
    }

    @Test
    fun `a fresh registry instance has no memory of a selection made in a previous instance — the selection does not survive a restart`() {
        val beforeRestart = ActiveStoreSelectionRegistry()
        beforeRestart.select(identityId, "session-1", velotrip)
        beforeRestart.activeStoreFor(identityId, "session-1") shouldBe velotrip

        val afterRestart = ActiveStoreSelectionRegistry()

        afterRestart.activeStoreFor(identityId, "session-1") shouldBe null
    }
}
