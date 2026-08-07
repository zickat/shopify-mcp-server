package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import org.junit.jupiter.api.Test

/**
 * `LOT0-06` — résolution de l'accès (identité + grant + boutique), en mémoire (fakes uniquement).
 * Le mécanisme de classification lecture/mutation (`ToolAccessControl`) est testé séparément
 * (`ToolAccessControlTest`) ; les deux sont exercés ensemble par `AccessMechanismEndToEndTest`.
 */
class AccessResolutionUseCaseTest {

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeRepository = StoreFakeRepository()
    private val grantRepository = GrantFakeRepository(identityExposedService, storeRepository)
    private val useCase = AccessResolutionUseCase(identityExposedService, grantRepository, storeRepository)

    private val issuer = "https://idp.test/"
    private val subject = "operator-1"

    private fun registerStore(archived: Boolean = false): String {
        val store = StoreFixtures().let { if (archived) it.archived() else it }.build()
        storeRepository.store[store.id.value] = store
        return store.id.value
    }

    private fun resolveIdentity(): String =
        identityExposedService.resolve(issuer, subject).fold({ error("resolve failed: $it") }, { it })

    private fun grantOn(storeId: String, role: GrantRole, identityId: String) {
        grantRepository.save(
            GrantFixtures()
                .withIdentityId(identityId)
                .withStoreId(StoreId(storeId))
                .withRole(role)
                .withGrantedBy(identityId)
                .build(),
        )
    }

    @Test
    fun `an authenticated identity with no grant at all is refused — no store visible, no tool callable`() {
        val storeId = registerStore()

        val result = useCase.resolve(issuer, subject, storeId)

        result.fold(
            { (it is ForbiddenError) shouldBe true },
            { error("expected a refusal, got a context") },
        )
    }

    @Test
    fun `an active viewer grant resolves to the viewer role`() {
        val storeId = registerStore()
        val identityId = resolveIdentity()
        grantOn(storeId, GrantRole.VIEWER, identityId)

        val result = useCase.resolve(issuer, subject, storeId)

        result.fold(
            { error("expected access, got $it") },
            { context ->
                context.tenant.storeId shouldBe storeId
                context.user.identityId shouldBe identityId
                context.user.role shouldBe AccessRole.VIEWER
            },
        )
    }

    @Test
    fun `an active operator grant resolves to the operator role`() {
        val storeId = registerStore()
        val identityId = resolveIdentity()
        grantOn(storeId, GrantRole.OPERATOR, identityId)

        val result = useCase.resolve(issuer, subject, storeId)

        result.fold(
            { error("expected access, got $it") },
            { context -> context.user.role shouldBe AccessRole.OPERATOR },
        )
    }

    @Test
    fun `a revoked grant is refused on the very next call — no cache, no wait for any expiration`() {
        val storeId = registerStore()
        val identityId = resolveIdentity()
        grantOn(storeId, GrantRole.OPERATOR, identityId)
        val grantId = grantRepository.store.values.single { it.identityId == identityId }.id

        val firstCall = useCase.resolve(issuer, subject, storeId)
        firstCall.isRight() shouldBe true

        // Révocation "entre deux appels" — pas de TTL, pas d'attente : mutation directe du grant,
        // exactement le scénario que schema.md §5 décrit ("effet immédiat").
        val revoked = grantRepository.store.getValue(grantId.value).copy(revokedAt = Clock.System.now())
        grantRepository.store[grantId.value] = revoked

        val secondCall = useCase.resolve(issuer, subject, storeId)
        secondCall.fold(
            { (it is ForbiddenError) shouldBe true },
            { error("expected the revoked grant to be refused immediately, got a context") },
        )
    }

    @Test
    fun `a store archived after the grant was created is refused too — defense in depth beyond grant revocation`() {
        val storeId = registerStore(archived = false)
        val identityId = resolveIdentity()
        grantOn(storeId, GrantRole.OPERATOR, identityId)

        val beforeArchiving = useCase.resolve(issuer, subject, storeId)
        beforeArchiving.isRight() shouldBe true

        val archivedStore = storeRepository.store.getValue(storeId).copy(archivedAt = Clock.System.now())
        storeRepository.store[storeId] = archivedStore

        val afterArchiving = useCase.resolve(issuer, subject, storeId)
        afterArchiving.fold(
            { (it is ForbiddenError) shouldBe true },
            { error("expected the archived store to be refused, got a context") },
        )
    }

    @Test
    fun `an unknown storeId is refused with the same error as a missing grant — no store enumeration`() {
        val identityId = resolveIdentity()
        val realStoreId = registerStore()
        grantOn(realStoreId, GrantRole.OPERATOR, identityId)

        val onMissingGrant = useCase.resolve(issuer, subject, realStoreId + "-does-not-exist")

        onMissingGrant.fold(
            { error ->
                (error is ForbiddenError) shouldBe true
                (error as ForbiddenError).messageKey shouldBe "access.denied"
            },
            { error("expected a refusal for an unknown store, got a context") },
        )
    }
}
