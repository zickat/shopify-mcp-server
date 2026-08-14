package com.zickat.shopifymcpserver.api.mcp

import arrow.core.right
import com.zickat.shopifymcpserver.api.exposed_interface.AuthenticatedToolPipeline
import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.audit.AuditLogFakeRepository
import com.zickat.shopifymcpserver.audit.exposed_interface.AuditExposedServiceImpl
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.relay.domain.RelayDispatcher
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGatewayImpl
import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.RelayTsClientFake
import com.zickat.shopifymcpserver.relay.domain.models.RelayContentBlock
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.spec.McpSchema
import java.time.Instant
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class RoutedToolPipelineTest {

    private object ReadUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
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

    private fun grantAccessAndSelect(sessionId: String) {
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, sessionId, storeId)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()
    }

    private fun routedPipeline(manifest: RelayManifest, tsClient: RelayTsClientFake): RoutedToolPipeline {
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, tsClient))
        return RoutedToolPipeline(pipeline, relayGateway, accessExposedService)
    }

    @Test
    fun `runForStore executes nativeAction and never reaches the relay when the manifest routes the tool NATIF`() {
        authenticateAs(subject)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityExposedService.resolve(issuer, subject).getOrNull()!!, AccessRole.OPERATOR)).right()
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(listOf(RelayManifestEntry("some_tool", ToolRoute.NATIF, UseCaseKind.READ)))
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val result = routed.runForStore("some_tool", ReadUseCase, storeId, emptyMap(), storeId) { _, _ ->
            nativeActionCalled = true
            McpSchema.CallToolResult.builder().addTextContent("native").build()
        }

        nativeActionCalled shouldBe true
        tsClient.callCount.get() shouldBe 0
        result.content().map { (it as McpSchema.TextContent).text() } shouldBe listOf("native")
    }

    @Test
    fun `runForStore never runs nativeAction and returns the relay's outcome when the manifest routes the tool RELAIS`() {
        authenticateAs(subject)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityExposedService.resolve(issuer, subject).getOrNull()!!, AccessRole.OPERATOR)).right()
        val tsClient = RelayTsClientFake().apply {
            nextResult = { RelayToolOutcome(listOf(RelayContentBlock("from the relay double")), isError = false).right() }
        }
        val manifest = RelayManifest(listOf(RelayManifestEntry("some_tool", ToolRoute.RELAIS, UseCaseKind.READ)))
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val result = routed.runForStore("some_tool", ReadUseCase, storeId, emptyMap(), storeId) { _, _ ->
            nativeActionCalled = true
            McpSchema.CallToolResult.builder().addTextContent("native").build()
        }

        nativeActionCalled shouldBe false
        tsClient.callCount.get() shouldBe 1
        result.content().map { (it as McpSchema.TextContent).text() } shouldBe listOf("from the relay double")
    }

    @Test
    fun `runForStore refuses and never reaches native or relay when the tool is absent from the manifest`() {
        authenticateAs(subject)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityExposedService.resolve(issuer, subject).getOrNull()!!, AccessRole.OPERATOR)).right()
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(emptyList())
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val exception = shouldThrow<UseCaseErrorException> {
            routed.runForStore("unmanifested_tool", ReadUseCase, storeId, emptyMap(), storeId) { _, _ ->
                nativeActionCalled = true
                McpSchema.CallToolResult.builder().addTextContent("native").build()
            }
        }

        nativeActionCalled shouldBe false
        tsClient.callCount.get() shouldBe 0
        exception.error.shouldBeInstanceOf<NotFoundError>().messageKey shouldBe "tool.not.in.manifest"
    }

    @Test
    fun `runForActiveStore executes nativeAction and never reaches the relay when the manifest routes the tool NATIF`() {
        authenticateAs(subject)
        grantAccessAndSelect("session-1")
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(listOf(RelayManifestEntry("some_tool", ToolRoute.NATIF, UseCaseKind.READ)))
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val result = routed.runForActiveStore("some_tool", ReadUseCase, "session-1", emptyMap()) { _, _ ->
            nativeActionCalled = true
            McpSchema.CallToolResult.builder().addTextContent("native").build()
        }

        nativeActionCalled shouldBe true
        tsClient.callCount.get() shouldBe 0
        result.content().map { (it as McpSchema.TextContent).text() } shouldBe listOf("native")
    }

    @Test
    fun `runForActiveStore never runs nativeAction and returns the relay's outcome when the manifest routes the tool RELAIS`() {
        authenticateAs(subject)
        grantAccessAndSelect("session-1")
        val tsClient = RelayTsClientFake().apply {
            nextResult = { RelayToolOutcome(listOf(RelayContentBlock("from the relay double")), isError = false).right() }
        }
        val manifest = RelayManifest(listOf(RelayManifestEntry("some_tool", ToolRoute.RELAIS, UseCaseKind.READ)))
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val result = routed.runForActiveStore("some_tool", ReadUseCase, "session-1", emptyMap()) { _, _ ->
            nativeActionCalled = true
            McpSchema.CallToolResult.builder().addTextContent("native").build()
        }

        nativeActionCalled shouldBe false
        tsClient.callCount.get() shouldBe 1
        result.content().map { (it as McpSchema.TextContent).text() } shouldBe listOf("from the relay double")
    }

    @Test
    fun `runForActiveStore refuses and never reaches native or relay when the tool is absent from the manifest`() {
        authenticateAs(subject)
        grantAccessAndSelect("session-1")
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(emptyList())
        val routed = routedPipeline(manifest, tsClient)
        var nativeActionCalled = false

        val exception = shouldThrow<UseCaseErrorException> {
            routed.runForActiveStore("unmanifested_tool", ReadUseCase, "session-1", emptyMap()) { _, _ ->
                nativeActionCalled = true
                McpSchema.CallToolResult.builder().addTextContent("native").build()
            }
        }

        nativeActionCalled shouldBe false
        tsClient.callCount.get() shouldBe 0
        exception.error.shouldBeInstanceOf<NotFoundError>().messageKey shouldBe "tool.not.in.manifest"
    }

    @Test
    fun `runForIdentity is a pure passthrough to the underlying pipeline — list_stores is not routed`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(emptyList())
        val routed = routedPipeline(manifest, tsClient)

        val result = routed.runForIdentity("list_stores", ReadUseCase, emptyMap()) { it }

        result shouldBe identityId
        tsClient.callCount.get() shouldBe 0
    }
}
