package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.ToolAccessControl
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class AccessMechanismEndToEndTest {

    private object ListingToolFixture : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    private object MutatingToolFixture : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    private val allTools = listOf(ListingToolFixture, MutatingToolFixture)

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeRepository = StoreFakeRepository()
    private val grantRepository = GrantFakeRepository(identityExposedService, storeRepository)
    private val accessResolution = AccessResolutionUseCase(identityExposedService, grantRepository, storeRepository)

    private val issuer = "https://idp.test/"
    private val subject = "operator-1"

    private fun registerStore(): String {
        val store = StoreFixtures().build()
        storeRepository.store[store.id.value] = store
        return store.id.value
    }

    private fun grant(storeId: String, role: GrantRole): String {
        val identityId = identityExposedService.resolve(issuer, subject).shouldBeRight()
        grantRepository.save(
            GrantFixtures()
                .withIdentityId(identityId)
                .withStoreId(StoreId(storeId))
                .withRole(role)
                .withGrantedBy(identityId)
                .build(),
        )
        return identityId
    }

    @Test
    fun `no grant at all — tools_list is empty and any tool call is refused with 403`() {
        val storeId = registerStore()

        val access = accessResolution.resolve(issuer, subject, storeId)

        access.shouldBeLeft().shouldBeInstanceOf<ForbiddenError>()
    }

    @Test
    fun `a viewer's tools_list hides the mutation tool, and a direct call to it is still refused server-side — the list is not the barrier`() {
        val storeId = registerStore()
        grant(storeId, GrantRole.VIEWER)

        val access = accessResolution.resolve(issuer, subject, storeId).shouldBeRight()

        val visible = ToolAccessControl.filterForList(access.user.role, allTools)
        visible shouldBe listOf(ListingToolFixture)

        val directCallOnHiddenTool = ToolAccessControl.authorizeCall(access.user.role, MutatingToolFixture)
        directCallOnHiddenTool.shouldBeLeft().shouldBeInstanceOf<ForbiddenError>()

        ToolAccessControl.authorizeCall(access.user.role, ListingToolFixture).isRight() shouldBe true
    }

    @Test
    fun `an operator sees and can call every tool of the store`() {
        val storeId = registerStore()
        grant(storeId, GrantRole.OPERATOR)

        val access = accessResolution.resolve(issuer, subject, storeId).shouldBeRight()

        ToolAccessControl.filterForList(access.user.role, allTools) shouldBe allTools
        ToolAccessControl.authorizeCall(access.user.role, ListingToolFixture).isRight() shouldBe true
        ToolAccessControl.authorizeCall(access.user.role, MutatingToolFixture).isRight() shouldBe true
    }
}
