package com.zickat.shopifymcpserver.api.mcp

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.AuditLogFakeRepository
import com.zickat.shopifymcpserver.audit.domain.AuditExposedServiceImpl
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.relay.domain.RelayDispatcher
import com.zickat.shopifymcpserver.relay.domain.RelayGatewayImpl
import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.RelayTsClientFake
import com.zickat.shopifymcpserver.relay.domain.models.RelayContentBlock
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.domain.models.ToolRoute
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayToolDescriptor
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class RelayToolRegistrarTest {

    @Serializable
    private data class ContentBlock(val type: String, val text: String)

    @Serializable
    private data class ToolOutput(val content: List<ContentBlock>)

    private val json = Json { ignoreUnknownKeys = true }

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
    fun `a relayed check_shopify_connection call reproduces the LOT1-01 cassette toolOutput, bit for bit`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, "session-1", storeId)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()

        val cassette = Cassette.fromClasspathResource("cassettes/check-shopify-connection.json")
        val expected = json.decodeFromJsonElement(ToolOutput.serializer(), cassette.toolOutput)

        val tsClient = RelayTsClientFake().apply {
            nextResult = { RelayToolOutcome(expected.content.map { RelayContentBlock(it.text) }, isError = false).right() }
        }
        val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, tsClient))
        val descriptor = RelayToolDescriptor("check_shopify_connection", UseCaseKind.READ)

        val result = relayToolCallResult(descriptor, relayGateway, pipeline, "session-1", emptyMap())

        result.isError() shouldBe false
        result.content().map { (it as io.modelcontextprotocol.spec.McpSchema.TextContent).text() } shouldBe expected.content.map { it.text }
        tsClient.calls.single().storeId shouldBe storeId
        tsClient.calls.single().role shouldBe "OPERATOR"
    }

    @Test
    fun `relayedTools only exposes tools listed RELAIS in the manifest`() {
        val manifest = RelayManifest(
            listOf(
                RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ),
                RelayManifestEntry("some_native_tool", ToolRoute.NATIF, UseCaseKind.MUTATION),
            ),
        )
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, RelayTsClientFake()))

        relayGateway.relayedTools().map { it.toolName } shouldBe listOf("check_shopify_connection")
    }

    @Test
    fun `a session with no active store selection is refused before the TS process is ever reached, and the refusal is journaled`() {
        authenticateAs(subject)
        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, tsClient))
        val descriptor = RelayToolDescriptor("check_shopify_connection", UseCaseKind.READ)

        val result = relayToolCallResult(descriptor, relayGateway, pipeline, "session-no-selection", emptyMap())

        result.isError() shouldBe true
        (result.content().first() as io.modelcontextprotocol.spec.McpSchema.TextContent).text() shouldContain "store.selection.missing"
        tsClient.callCount.get() shouldBe 0
        auditRepository.entries.single().outcome shouldBe "denied"
    }

    @Test
    fun `a relay failure (TS unreachable) surfaces as an isError CallToolResult naming the technical error, never an unhandled exception`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, "session-1", storeId)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.OPERATOR)).right()

        val tsClient = RelayTsClientFake().apply {
            nextResult = { TechnicalError("relay.ts.network.failed").left() }
        }
        val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, tsClient))
        val descriptor = RelayToolDescriptor("check_shopify_connection", UseCaseKind.READ)

        val result = relayToolCallResult(descriptor, relayGateway, pipeline, "session-1", emptyMap())

        result.isError() shouldBe true
        (result.content().first() as io.modelcontextprotocol.spec.McpSchema.TextContent).text() shouldContain "relay.ts.network.failed"
    }

    @Test
    fun `an insufficient role for a relayed MUTATION tool is refused server-side, before the TS process is ever reached`() {
        authenticateAs(subject)
        val identityId = identityExposedService.resolve(issuer, subject).getOrNull()!!
        activeStoreExposedService.select(identityId, "session-1", storeId)
        accessExposedService.result = (TenantContext(storeId) to UserContext(identityId, AccessRole.VIEWER)).right()

        val tsClient = RelayTsClientFake()
        val manifest = RelayManifest(listOf(RelayManifestEntry("relayed_mutation_tool", ToolRoute.RELAIS, UseCaseKind.MUTATION)))
        val relayGateway = RelayGatewayImpl(manifest, RelayDispatcher(manifest, tsClient))
        val descriptor = RelayToolDescriptor("relayed_mutation_tool", UseCaseKind.MUTATION)

        val result = relayToolCallResult(descriptor, relayGateway, pipeline, "session-1", emptyMap())

        result.isError() shouldBe true
        (result.content().first() as io.modelcontextprotocol.spec.McpSchema.TextContent).text() shouldContain "access.role.insufficient"
        tsClient.callCount.get() shouldBe 0
    }
}
