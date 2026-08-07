package com.zickat.shopifymcpserver.api.mcp

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.AuditLogFakeRepository
import com.zickat.shopifymcpserver.audit.domain.AuditExposedServiceImpl
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

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
    private val storeExposedService = StoreExposedServiceFake().apply { archivedByStoreId[storeId] = false }
    private val auditRepository = AuditLogFakeRepository(identityExposedService, storeExposedService)
    private val auditExposedService = AuditExposedServiceImpl(AuditLogUseCase(auditRepository))
    private val pipeline = AuthenticatedToolPipeline(identityExposedService, accessExposedService, auditExposedService)

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
    fun `should deny closed and journal with a null identityId when there is no authenticated principal at all`() {
        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.run("whoami", ReadUseCase, storeId, mapOf("storeId" to storeId)) { _, _ -> "unreachable" }
        }

        exception.error.let { (it is com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError) shouldBe true }
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe null
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "identity.principal.missing"
    }

    @Test
    fun `should deny and journal when access resolution refuses — no active grant`() {
        authenticateAs(subject)
        accessExposedService.result = ForbiddenError("access.denied", mapOf("storeId" to storeId)).left()

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.run("whoami", ReadUseCase, storeId, mapOf("storeId" to storeId)) { _, _ -> "unreachable" }
        }

        (exception.error is ForbiddenError) shouldBe true
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityExposedService.resolve(issuer, subject).getOrNull()
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "access.denied"
    }

    @Test
    fun `should deny and journal when the role is insufficient for a mutation use case, even though access itself was granted`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.VIEWER)).right()

        val exception = shouldThrow<UseCaseErrorException> {
            pipeline.run("touch_store", MutationUseCase, storeId, mapOf("storeId" to storeId)) { _, _ -> "unreachable" }
        }

        (exception.error is ForbiddenError) shouldBe true
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.isMutation shouldBe true
        entry.outcome shouldBe "denied"
        entry.denialReason shouldBe "access.role.insufficient"
    }

    @Test
    fun `should run the action and journal ok when an operator grant authorizes a mutation use case`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()

        val result = pipeline.run("touch_store", MutationUseCase, storeId, mapOf("storeId" to storeId)) { tenant, user ->
            "${tenant.storeId}:${user.identityId}:${user.role}"
        }

        result shouldBe "$storeId:$identityId:OPERATOR"
        val entry = auditRepository.entries.single()
        entry.identityId shouldBe identityId
        entry.outcome shouldBe "ok"
        entry.toolName shouldBe "touch_store"
    }

    @Test
    fun `should pass the exact issuer, subject and storeId to access resolution`() {
        authenticateAs(subject)
        identityExposedService.resolve(issuer, subject)
        accessExposedService.result = ForbiddenError("access.denied").left()

        shouldThrow<UseCaseErrorException> {
            pipeline.run("whoami", ReadUseCase, storeId, mapOf("storeId" to storeId)) { _, _ -> "unreachable" }
        }

        accessExposedService.lastCall shouldBe Triple(issuer, subject, storeId)
    }
}
