package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.AuthenticatedToolPipeline
import com.zickat.shopifymcpserver.audit.AuditLogFakeRepository
import com.zickat.shopifymcpserver.audit.exposed_interface.AuditExposedServiceImpl
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class AuthenticatedToolPipelineTest {

    private object ReadUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    private object MutationUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    private val issuer = "https://idp.test/"
    private val subject = "operator-1"
    private val storeId = ObjectId().toHexString()

    private val identityExposedService = IdentityExposedServiceFake()
    private val accessExposedService = AccessExposedServiceFake()
    private val activeStoreExposedService = ActiveStoreExposedServiceFake()
    private val auditRepository = AuditLogFakeRepository(identityExposedService)
    private val auditExposedService = AuditExposedServiceImpl(AuditLogUseCase(auditRepository))
    private val pipeline = AuthenticatedToolPipeline(identityExposedService, accessExposedService, auditExposedService, activeStoreExposedService)

    private fun authenticateAs(subject: String) {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("iss", issuer)
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `runForStore should deny closed and journal with a null identityId when there is no authenticated principal at all`() {
        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForStore("use_store", MutationUseCase, storeId, mapOf("storeId" to storeId), storeId) { _, _ -> "unreachable" }
        }

        exception.error.let { (it is com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError) shouldBe true }
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe null
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "identity.principal.missing"
    }

    @Test
    fun `runForStore should deny and journal when access resolution refuses, naming the identity's actually granted stores`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.result = ForbiddenError("access.denied", mapOf("storeId" to storeId)).left()
        accessExposedService.grantedStoresByIdentity[identityId] =
            listOf(GrantedStore(storeId = ObjectId().toHexString(), slug = "velotrip"))

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForStore("use_store", MutationUseCase, storeId, mapOf("storeId" to storeId), storeId) { _, _ -> "unreachable" }
        }

        (exception.error is ForbiddenError) shouldBe true
        exception.message.orEmpty() shouldContain "velotrip"
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "access.denied"
    }

    @Test
    fun `runForStore should deny and journal when the role is insufficient for a mutation use case, even though access itself was granted`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.VIEWER)).right()

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForStore("use_store", MutationUseCase, storeId, mapOf("storeId" to storeId), storeId) { _, _ -> "unreachable" }
        }

        (exception.error is ForbiddenError) shouldBe true
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.isMutation shouldBe true
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "access.role.insufficient"
    }

    @Test
    fun `runForStore should run the action and journal ok when an operator grant authorizes a mutation use case`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()

        val result = pipeline.runForStore("use_store", MutationUseCase, storeId, mapOf("storeId" to storeId), storeId) { tenant, user ->
            "${tenant.storeId}:${user.identityId}:${user.role}"
        }

        result shouldBe "$storeId:$identityId:OPERATOR"
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.outcome shouldBe "ok"
        entry.toolName shouldBe "use_store"
    }

    @Test
    fun `runForStore should pass the exact issuer, subject and storeId to access resolution`() {
        authenticateAs(subject)
        identityExposedService.resolve(issuer, subject)
        accessExposedService.result = ForbiddenError("access.denied").left()

        shouldThrow<UseCaseErrorException> {
            pipeline.runForStore("use_store", MutationUseCase, storeId, mapOf("storeId" to storeId), storeId) { _, _ -> "unreachable" }
        }

        accessExposedService.lastCall shouldBe Triple(issuer, subject, storeId)
    }

    @Test
    fun `runForIdentity should run the action with the resolved identityId and journal ok with an empty storeId`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!

        val result = pipeline.runForIdentity("list_stores", ReadUseCase, emptyMap()) { it }

        result shouldBe identityId
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.storeId shouldBe ""
        entry.outcome shouldBe "ok"
        entry.toolName shouldBe "list_stores"
    }

    @Test
    fun `runForIdentity should deny closed with a null identityId when there is no authenticated principal at all`() {
        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForIdentity("list_stores", ReadUseCase, emptyMap()) { "unreachable" }
        }

        exception.error.let { (it is com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError) shouldBe true }
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe null
    }

    @Test
    fun `runForActiveStore should refuse and name the identity's granted stores when this session made no selection — this is the refusal a future store-scoped tool relies on`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.grantedStoresByIdentity[identityId] = listOf(
            GrantedStore(storeId = ObjectId().toHexString(), slug = "velotrip"),
            GrantedStore(storeId = ObjectId().toHexString(), slug = "lurelab"),
        )

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForActiveStore("future_catalog_tool", ReadUseCase, "session-1", emptyMap()) { _, _ -> "unreachable" }
        }

        (exception.error is ForbiddenError) shouldBe true
        (exception.error as ForbiddenError).messageKey shouldBe "store.selection.missing"
        exception.message.orEmpty() shouldContain "velotrip"
        exception.message.orEmpty() shouldContain "lurelab"
        val entry = auditRepository.entries.single()
        entry.storeId shouldBe ""
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "store.selection.missing"
    }

    @Test
    fun `runForActiveStore should resolve access against the session's active store and run the action when one was selected`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, "session-1", storeId)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()

        val result = pipeline.runForActiveStore("future_catalog_tool", ReadUseCase, "session-1", emptyMap()) { tenant, _ ->
            tenant.storeId
        }

        result shouldBe storeId
        accessExposedService.lastCall shouldBe Triple(issuer, subject, storeId)
        val entry = auditRepository.entries.single()
        entry.storeId shouldBe storeId
        entry.outcome shouldBe "ok"
    }

    @Test
    fun `runForActiveStore should not see a selection made under a different sessionId of the same identity`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, "session-A", storeId)

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.runForActiveStore("future_catalog_tool", ReadUseCase, "session-B", emptyMap()) { _, _ -> "unreachable" }
        }

        (exception.error as ForbiddenError).messageKey shouldBe "store.selection.missing"
    }
}
