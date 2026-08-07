package com.zickat.shopifymcpserver.audit

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

/**
 * `LOT0-07.md`, « comment on vérifie que c'est fini ». Toute l'écriture passe par des fakes — pas
 * de Spring, pas de MongoDB, `testing.md` §Tests unitaires.
 */
class AuditLogUseCaseTest {

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeExposedService = StoreExposedServiceFake()
    private val repository = AuditLogFakeRepository(identityExposedService, storeExposedService)
    private val useCase = AuditLogUseCase(repository, Clock.System)

    private fun registerStore(): String {
        val id = ObjectId().toHexString()
        storeExposedService.existing[id] = false // existe, pas archivée
        return id
    }

    private fun registerIdentity(): String {
        val id = ObjectId().toHexString()
        identityExposedService.existingIds.add(id)
        return id
    }

    // --- Nominal : tous les champs de schema.md §3 sont présents -----------------------------

    @Test
    fun `should write every field of schema md section 3 and return the action's result on success`() {
        val storeId = registerStore()
        val identityId = registerIdentity()

        val result = useCase.execute(
            identityId = identityId,
            storeId = storeId,
            toolName = "list_products",
            isMutation = false,
            toolInput = mapOf("query" to "shoes"),
        ) { "business result".right() }

        result shouldBe "business result".right()
        repository.entries shouldHaveSize 1
        val entry = repository.entries.single()
        entry.identityId shouldBe identityId
        entry.storeId shouldBe storeId
        entry.toolName shouldBe "list_products"
        entry.isMutation shouldBe false
        entry.outcome shouldBe "ok"
        entry.denialReason shouldBe null
        entry.toolInputDigest.keys shouldBe setOf("sha256")
    }

    @Test
    fun `should accept a null identityId — an unauthenticated call is still journaled`() {
        val storeId = registerStore()

        useCase.execute(identityId = null, storeId = storeId, toolName = "list_products", isMutation = false, toolInput = mapOf()) {
            "ok".right()
        }

        repository.entries.single().identityId shouldBe null
    }

    // --- Les trois outcome : ok, denied, error ------------------------------------------------

    @Test
    fun `outcome should be ok when the action succeeds`() {
        val storeId = registerStore()

        useCase.execute(identityId = null, storeId = storeId, toolName = "t", isMutation = false, toolInput = mapOf()) {
            "done".right()
        }

        repository.entries.single().outcome shouldBe "ok"
    }

    @Test
    fun `outcome should be denied with the forbidden error's messageKey as denialReason when access is refused`() {
        val storeId = registerStore()

        val result = useCase.execute(identityId = null, storeId = storeId, toolName = "t", isMutation = true, toolInput = mapOf()) {
            ForbiddenError("access.role.insufficient", mapOf("requiredRole" to "OPERATOR")).left()
        }

        result.isLeft() shouldBe true
        val entry = repository.entries.single()
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "access.role.insufficient"
    }

    @Test
    fun `outcome should be denied for an unauthenticated caller too, with its messageKey as denialReason`() {
        val storeId = registerStore()

        useCase.execute(identityId = null, storeId = storeId, toolName = "t", isMutation = false, toolInput = mapOf()) {
            NotAuthorizedError("auth.token.invalid").left()
        }

        val entry = repository.entries.single()
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "auth.token.invalid"
    }

    @Test
    fun `outcome should be error, with no denialReason, when the action fails for a non-access reason`() {
        val storeId = registerStore()

        useCase.execute(identityId = null, storeId = storeId, toolName = "t", isMutation = false, toolInput = mapOf()) {
            NotFoundError("product.not.found").left()
        }

        val entry = repository.entries.single()
        entry.outcome shouldBe "error"
        entry.denialReason shouldBe null
    }

    // --- Le test le plus important : fail-closed sur l'écriture d'audit elle-même -------------

    @Test
    fun `should fail the whole request with a TechnicalError when the audit write itself fails, even though the business action succeeded`() {
        val storeId = registerStore()
        repository.shouldFailAppend = true
        var actionRan = false

        val result = useCase.execute(identityId = null, storeId = storeId, toolName = "mutate_x", isMutation = true, toolInput = mapOf()) {
            actionRan = true // l'action métier a bien pu s'exécuter (ex. mutation Shopify au lot 2)…
            "mutated".right()
        }

        // …mais rien de tout ça n'est considéré comme fait par l'appelant : le contrat retourné
        // est un échec, et RIEN n'a été journalisé — pas même une trace partielle.
        actionRan shouldBe true
        result.fold(
            { (it is TechnicalError) shouldBe true },
            { error("expected a TechnicalError, got a success: $it") },
        )
        repository.entries.shouldBeEmpty()
    }

    @Test
    fun `should fail closed the same way when the action itself had already failed — an unlogged denial is not acceptable either`() {
        val storeId = registerStore()
        repository.shouldFailAppend = true

        val result = useCase.execute(identityId = null, storeId = storeId, toolName = "t", isMutation = false, toolInput = mapOf()) {
            ForbiddenError("access.denied", mapOf("storeId" to storeId)).left()
        }

        // Le refus d'accès existait, mais s'il ne peut pas être journalisé, l'appelant ne doit
        // pas recevoir silencieusement ce ForbiddenError d'origine : fail-closed s'applique
        // uniformément, quel que soit ce que l'action avait produit.
        result.fold(
            { (it is TechnicalError) shouldBe true },
            { error("expected a TechnicalError, got a success: $it") },
        )
        repository.entries.shouldBeEmpty()
    }
}
