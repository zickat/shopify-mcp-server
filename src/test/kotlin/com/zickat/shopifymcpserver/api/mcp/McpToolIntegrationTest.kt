package com.zickat.shopifymcpserver.api.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Clock
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.Date

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class McpToolIntegrationTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var grantRepository: GrantRepository

    @Autowired
    private lateinit var identityExposedService: IdentityExposedService

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    private val objectMapper = ObjectMapper()

    companion object {
        private const val EXPECTED_AUDIENCE = "https://shopify-mcp-server.test/mcp"
        private const val ISSUER_URI = "https://idp.test.local/"

        private val key: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()

        private val jwksServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(JWKSet(key.toPublicJWK()).toString())
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                jwksServer.url("/jwks").toString()
            }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { ISSUER_URI }
            registry.add("mcp.security.expected-audience") { EXPECTED_AUDIENCE }
        }

        @JvmStatic
        @AfterAll
        fun shutdownJwksServer() {
            jwksServer.shutdown()
        }

        private fun jwt(subject: String): String {
            val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build()
            val claims = JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER_URI)
                .audience(listOf(EXPECTED_AUDIENCE))
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build()
            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(RSASSASigner(key))
            return signedJwt.serialize()
        }

        private fun rpcHeaders(token: String? = null, sessionId: String? = null): HttpHeaders {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.accept = listOf(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
            token?.let { headers.setBearerAuth(it) }
            sessionId?.let { headers.set("Mcp-Session-Id", it) }
            return headers
        }
    }

    private fun sendJsonRpcRequestAndParseJsonPayload(body: String, token: String? = null, sessionId: String? = null): Pair<org.springframework.http.ResponseEntity<String>, Map<String, Any?>?> {
        val response = restTemplate.exchange(
            "/mcp",
            HttpMethod.POST,
            HttpEntity(body, rpcHeaders(token, sessionId)),
            String::class.java,
        )
        val payload = extractJsonPayload(response.body, response.headers.contentType?.toString())
        return response to payload
    }

    private fun extractJsonPayload(body: String?, contentType: String?): Map<String, Any?>? {
        if (body.isNullOrBlank()) return null
        val jsonText = if (contentType?.contains("text/event-stream") == true) {
            body.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .lastOrNull { it.isNotBlank() }
                ?: return null
        } else {
            body
        }
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(jsonText, Map::class.java) as Map<String, Any?>
    }

    private fun handshake(token: String): String {
        val initResponse = restTemplate.exchange(
            "/mcp",
            HttpMethod.POST,
            HttpEntity(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"lot0-08-test","version":"0.0.0"}}}""",
                rpcHeaders(token),
            ),
            String::class.java,
        )
        initResponse.statusCode shouldBe HttpStatus.OK
        val sessionId = initResponse.headers.getFirst("Mcp-Session-Id")
        checkNotNull(sessionId) { "no Mcp-Session-Id header on initialize — cannot proceed" }

        val notifyResponse = restTemplate.exchange(
            "/mcp",
            HttpMethod.POST,
            HttpEntity("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", rpcHeaders(token, sessionId)),
            String::class.java,
        )
        notifyResponse.statusCode shouldBe HttpStatus.ACCEPTED

        return sessionId
    }

    private fun toolsList(token: String, sessionId: String) =
        sendJsonRpcRequestAndParseJsonPayload("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", token, sessionId)

    private fun toolsCall(token: String, sessionId: String, toolName: String, storeId: String) =
        sendJsonRpcRequestAndParseJsonPayload(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"$toolName","arguments":{"storeId":"$storeId"}}}""",
            token,
            sessionId,
        )

    private fun registerStore(): String =
        storeRepository.save(StoreFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    private fun resolveIdentity(subject: String): String =
        identityExposedService.resolve(ISSUER_URI, subject).fold({ error("identity resolution failed: $it") }, { it })

    private fun grant(identityId: String, storeId: String, role: GrantRole) {
        grantRepository.save(
            Grant(
                id = GrantId(ObjectId().toHexString()),
                identityId = identityId,
                storeId = StoreId(storeId),
                role = role,
                grantedBy = identityId,
                createdAt = Clock.System.now(),
                revokedAt = null,
            ),
        ).fold({ error("grant setup failed: $it") }, { it })
    }

    @Test
    fun `full handshake with a token — initialize, notifications initialized, tools list, tools call whoami`() {
        val storeId = registerStore()
        val identityId = resolveIdentity("operator-full-handshake")
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt("operator-full-handshake")

        val sessionId = handshake(token)

        val (listResponse, listPayload) = toolsList(token, sessionId)
        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        (tools.any { (it as? Map<*, *>)?.get("name") == "whoami" }) shouldBe true

        val (callResponse, callPayload) = toolsCall(token, sessionId, "whoami", storeId)
        callResponse.statusCode shouldBe HttpStatus.OK
        val result = callPayload?.get("result") as? Map<*, *>
        checkNotNull(result) { "tools/call did not return a result: $callPayload" }
        result["isError"] shouldBe false
    }

    @Test
    fun `initialize without any token is rejected with 401, before any tool wiring even runs`() {
        val response = restTemplate.exchange(
            "/mcp",
            HttpMethod.POST,
            HttpEntity(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"0"}}}""",
                rpcHeaders(token = null),
            ),
            String::class.java,
        )

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `an authenticated identity with no grant is refused on tools call as a 200 CallToolResult with isError, not an HTTP error status, and the refusal is journaled`() {
        val storeId = registerStore()
        val subject = "operator-no-grant"
        resolveIdentity(subject)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(token, sessionId, "whoami", storeId)

        callResponse.statusCode shouldBe HttpStatus.OK
        val result = callPayload?.get("result") as? Map<*, *>
        checkNotNull(result) { "tools/call did not return a result: $callPayload" }
        result["isError"] shouldBe true
        val text = ((result["content"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("text") as? String
        text.orEmpty() shouldContain "access.denied"

        val identityId = resolveIdentity(subject)
        val entries = auditLogRepository.findByStore(storeId).fold({ error("audit read failed: $it") }, { it })
        val deniedEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "whoami" }
        checkNotNull(deniedEntry) { "no audit entry written for the denied call — audit-before-response is broken" }
        deniedEntry.outcome shouldBe "denied"
        deniedEntry.denialReason shouldBe "access.denied"
    }

    @Test
    fun `a viewer calling the mutation tool directly is refused server-side while whoami (READ) remains callable, regardless of tools list, and the refusal is journaled`() {
        val storeId = registerStore()
        val subject = "viewer-touch-store"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)

        val (whoamiResponse, whoamiPayload) = toolsCall(token, sessionId, "whoami", storeId)
        whoamiResponse.statusCode shouldBe HttpStatus.OK
        (whoamiPayload?.get("result") as? Map<*, *>)?.get("isError") shouldBe false

        val (touchResponse, touchPayload) = toolsCall(token, sessionId, "touch_store", storeId)
        touchResponse.statusCode shouldBe HttpStatus.OK
        val touchResult = touchPayload?.get("result") as? Map<*, *>
        checkNotNull(touchResult) { "tools/call did not return a result: $touchPayload" }
        touchResult["isError"] shouldBe true
        val text = ((touchResult["content"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("text") as? String
        text.orEmpty() shouldContain "access.role.insufficient"

        val entries = auditLogRepository.findByStore(storeId).fold({ error("audit read failed: $it") }, { it })
        val deniedEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "touch_store" }
        checkNotNull(deniedEntry) { "no audit entry written for the role-insufficient refusal" }
        deniedEntry.outcome shouldBe "denied"
        deniedEntry.denialReason shouldBe "access.role.insufficient"
        deniedEntry.isMutation shouldBe true
    }

    @Test
    fun `an operator can call the mutation tool, and the success is journaled`() {
        val storeId = registerStore()
        val subject = "operator-touch-store"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (touchResponse, touchPayload) = toolsCall(token, sessionId, "touch_store", storeId)

        touchResponse.statusCode shouldBe HttpStatus.OK
        val touchResult = touchPayload?.get("result") as? Map<*, *>
        checkNotNull(touchResult) { "tools/call did not return a result: $touchPayload" }
        touchResult["isError"] shouldBe false

        val entries = auditLogRepository.findByStore(storeId).fold({ error("audit read failed: $it") }, { it })
        val okEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "touch_store" }
        checkNotNull(okEntry) { "no audit entry written for the successful mutation call" }
        okEntry.outcome shouldBe "ok"
        okEntry.isMutation shouldBe true
    }
}
