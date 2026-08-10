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
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
    private lateinit var identityRepository: IdentityRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var activeStoreExposedService: ActiveStoreExposedService

    @Autowired
    private lateinit var relayToolContracts: RelayToolContracts

    @Autowired
    private lateinit var relayGateway: RelayGateway

    @Autowired
    private lateinit var nativeToolNames: NativeToolNames

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

        private val relayTsDouble = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"content":[{"type":"text","text":"Connexion Shopify OK — boutique : Test Store"}],"isError":false}""")
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
            registry.add("relay.ts.base-url") { relayTsDouble.url("").toString().trimEnd('/') }
        }

        @JvmStatic
        @AfterAll
        fun shutdownJwksServer() {
            jwksServer.shutdown()
            relayTsDouble.shutdown()
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
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"lot2-02-test","version":"0.0.0"}}}""",
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

    private fun toolsCall(token: String, sessionId: String, toolName: String, arguments: Map<String, String> = emptyMap()): Pair<org.springframework.http.ResponseEntity<String>, Map<String, Any?>?> {
        val argumentsJson = objectMapper.writeValueAsString(arguments)
        return sendJsonRpcRequestAndParseJsonPayload(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"$toolName","arguments":$argumentsJson}}""",
            token,
            sessionId,
        )
    }

    private fun listStores(token: String, sessionId: String) = toolsCall(token, sessionId, "list_stores")

    private fun useStore(token: String, sessionId: String, storeId: String) =
        toolsCall(token, sessionId, "use_store", mapOf("store_id" to storeId))

    private fun toolResultTexts(payload: Map<String, Any?>?): Pair<Boolean, List<String>> {
        val result = payload?.get("result") as? Map<*, *>
        checkNotNull(result) { "tools/call did not return a result: $payload" }
        val isError = result["isError"] as? Boolean ?: false
        val texts = (result["content"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
        return isError to texts
    }

    private fun toolResultText(payload: Map<String, Any?>?): Pair<Boolean, String> {
        val result = payload?.get("result") as? Map<*, *>
        checkNotNull(result) { "tools/call did not return a result: $payload" }
        val isError = result["isError"] as? Boolean ?: false
        val text = ((result["content"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("text") as? String
        return isError to text.orEmpty()
    }

    private fun registerStore(): String =
        storeRepository.save(StoreFixtures().build()).shouldBeRight().id.value

    private fun registerStore(slug: String): String =
        storeRepository.save(StoreFixtures().withSlug(slug).build()).shouldBeRight().id.value

    private fun resolveIdentity(subject: String): String =
        identityExposedService.resolve(ISSUER_URI, subject).shouldBeRight()

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
        ).shouldBeRight()
    }

    @Test
    fun `full handshake with a token — initialize, notifications initialized, tools list, tools call list_stores`() {
        val storeId = registerStore()
        val identityId = resolveIdentity("operator-full-handshake")
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt("operator-full-handshake")

        val sessionId = handshake(token)

        val (listResponse, listPayload) = toolsList(token, sessionId)
        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        (tools.any { (it as? Map<*, *>)?.get("name") == "list_stores" }) shouldBe true
        (tools.any { (it as? Map<*, *>)?.get("name") == "use_store" }) shouldBe true
        (tools.any { (it as? Map<*, *>)?.get("name") == "whoami" }) shouldBe false
        (tools.any { (it as? Map<*, *>)?.get("name") == "touch_store" }) shouldBe false

        val (callResponse, callPayload) = listStores(token, sessionId)
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
    fun `use_store against a store the identity has no grant on is refused as a 200 CallToolResult with isError, not an HTTP error status, and the refusal is journaled`() {
        val storeId = registerStore("no-grant-store")
        val subject = "operator-no-grant"
        resolveIdentity(subject)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = useStore(token, sessionId, "no-grant-store")

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "access.denied"
        text shouldNotContain storeId

        val identityId = resolveIdentity(subject)
        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val deniedEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "use_store" }
        checkNotNull(deniedEntry) { "no audit entry written for the denied call — audit-before-response is broken" }
        deniedEntry.outcome shouldBe "denied"
        deniedEntry.denialReason shouldBe "access.denied"
    }

    @Test
    fun `a viewer end to end — selects its granted store via use_store (READ), reads through search_resources, and is refused only on the create_redirect mutation`() {
        val storeId = registerStore()
        val subject = "viewer-end-to-end"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)

        val (listStoresResponse, listStoresPayload) = listStores(token, sessionId)
        listStoresResponse.statusCode shouldBe HttpStatus.OK
        (listStoresPayload?.get("result") as? Map<*, *>)?.get("isError") shouldBe false

        val (useStoreResponse, useStorePayload) = useStore(token, sessionId, storeId)
        useStoreResponse.statusCode shouldBe HttpStatus.OK
        val (useStoreIsError, _) = toolResultText(useStorePayload)
        useStoreIsError shouldBe false

        val (searchResponse, searchPayload) = toolsCall(token, sessionId, "search_resources", mapOf("resource_type" to "collection"))
        searchResponse.statusCode shouldBe HttpStatus.OK
        val (searchIsError, searchTexts) = toolResultTexts(searchPayload)
        searchIsError shouldBe true
        searchTexts.joinToString("\n") shouldNotContain "access.role.insufficient"

        val (redirectResponse, redirectPayload) = toolsCall(token, sessionId, "create_redirect", mapOf("from_path" to "/a", "to_path" to "/b"))
        redirectResponse.statusCode shouldBe HttpStatus.OK
        val (redirectIsError, redirectText) = toolResultText(redirectPayload)
        redirectIsError shouldBe true
        redirectText shouldContain "access.role.insufficient"

        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val useStoreEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "use_store" }
        checkNotNull(useStoreEntry) { "no audit entry written for the successful use_store call" }
        useStoreEntry.outcome shouldBe "ok"
        useStoreEntry.isMutation shouldBe false

        val redirectDeniedEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "create_redirect" }
        checkNotNull(redirectDeniedEntry) { "no audit entry written for the role-insufficient refusal on create_redirect" }
        redirectDeniedEntry.outcome shouldBe "denied"
        redirectDeniedEntry.denialReason shouldBe "access.role.insufficient"
        redirectDeniedEntry.isMutation shouldBe true
    }

    @Test
    fun `use_store against a storeId that does not exist is refused as access denied, not a technical error, and the probe is journaled`() {
        val subject = "prober-unknown-store"
        resolveIdentity(subject)
        val token = jwt(subject)
        val unknownStoreId = ObjectId().toHexString()

        val sessionId = handshake(token)
        val (callResponse, callPayload) = useStore(token, sessionId, unknownStoreId)

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "access.denied"

        val entries = auditLogRepository.findByStore(unknownStoreId).shouldBeRight()
        val deniedEntry = entries.firstOrNull { it.toolName == "use_store" }
        checkNotNull(deniedEntry) { "no audit entry written for a probe on an unknown storeId — this is exactly what the log must capture" }
        deniedEntry.outcome shouldBe "denied"
        deniedEntry.denialReason shouldBe "access.denied"
    }

    @Test
    fun `use_store against a malformed storeId is refused as access denied for every guessable shape, never an unhandled exception, and each probe is journaled`() {
        val subject = "prober-malformed-store"
        resolveIdentity(subject)
        val token = jwt(subject)
        val sessionId = handshake(token)

        listOf("velotrip", "velotrip.myshopify.com", "abc123").forEach { malformedStoreId ->
            val (callResponse, callPayload) = useStore(token, sessionId, malformedStoreId)

            callResponse.statusCode shouldBe HttpStatus.OK
            val (isError, text) = toolResultText(callPayload)
            isError shouldBe true
            text shouldContain "access.denied"

            val entries = auditLogRepository.findByStore(malformedStoreId).shouldBeRight()
            val deniedEntry = entries.firstOrNull { it.toolName == "use_store" }
            checkNotNull(deniedEntry) { "no audit entry written for a probe on malformed storeId '$malformedStoreId'" }
            deniedEntry.outcome shouldBe "denied"
            deniedEntry.denialReason shouldBe "access.denied"
        }
    }

    @Test
    fun `an operator can call use_store with a slug, and the success is journaled under the canonical ObjectId`() {
        val storeId = registerStore("operator-use-store-slug")
        val subject = "operator-use-store"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (useStoreResponse, useStorePayload) = useStore(token, sessionId, "operator-use-store-slug")

        useStoreResponse.statusCode shouldBe HttpStatus.OK
        val (isError, _) = toolResultText(useStorePayload)
        isError shouldBe false

        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val okEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "use_store" }
        checkNotNull(okEntry) { "no audit entry written for the successful call" }
        okEntry.outcome shouldBe "ok"
        okEntry.isMutation shouldBe false
    }

    @Test
    fun `list_stores shows no active store until use_store is called in that same session`() {
        val storeId = registerStore("list-before-select")
        val subject = "operator-list-before-select"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (_, beforePayload) = listStores(token, sessionId)
        val (_, beforeText) = toolResultText(beforePayload)
        beforeText shouldContain "Aucune boutique sélectionnée"

        useStore(token, sessionId, "list-before-select")

        val (_, afterPayload) = listStores(token, sessionId)
        val (_, afterText) = toolResultText(afterPayload)
        afterText shouldContain "active"
    }

    @Test
    fun `a revoked identity sees nothing through list_stores, not only through use_store — D17 covers the whole surface`() {
        val storeId = registerStore("revoked-list-stores-slug")
        val subject = "revoked-list-stores"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (beforeResponse, beforePayload) = listStores(token, sessionId)
        beforeResponse.statusCode shouldBe HttpStatus.OK
        val (beforeIsError, beforeText) = toolResultText(beforePayload)
        beforeIsError shouldBe false
        beforeText shouldNotContain "Aucune boutique accordée"

        val identity = identityRepository.findById(IdentityId(identityId)).shouldBeRight()
        identityRepository.save(identity.copy(revokedAt = Clock.System.now())).shouldBeRight()

        val (afterListResponse, afterListPayload) = listStores(token, sessionId)
        afterListResponse.statusCode shouldBe HttpStatus.OK
        val (afterListIsError, afterListText) = toolResultText(afterListPayload)
        afterListIsError shouldBe false
        afterListText shouldContain "Aucune boutique accordée à cette identité."

        val (useStoreResponse, useStorePayload) = useStore(token, sessionId, "revoked-list-stores-slug")
        useStoreResponse.statusCode shouldBe HttpStatus.OK
        val (useStoreIsError, useStoreText) = toolResultText(useStorePayload)
        useStoreIsError shouldBe true
        useStoreText shouldContain "access.denied"
    }

    @Test
    fun `two real MCP sessions of the same identity never share their active store — this is the invariant that replaced the per-identity persisted selection`() {
        val velotripId = registerStore("velotrip")
        val lurelabId = registerStore("lurelab")
        val subject = "operator-two-sessions"
        val identityId = resolveIdentity(subject)
        grant(identityId, velotripId, GrantRole.OPERATOR)
        grant(identityId, lurelabId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionDesktop = handshake(token)
        val sessionCode = handshake(token)
        (sessionDesktop == sessionCode) shouldBe false

        useStore(token, sessionDesktop, "velotrip")
        useStore(token, sessionCode, "lurelab")

        val (_, desktopListing) = listStores(token, sessionDesktop)
        val (_, desktopText) = toolResultText(desktopListing)
        (desktopText.lines().any { it.contains("velotrip") && it.contains("active") }) shouldBe true
        (desktopText.lines().any { it.contains("lurelab") && it.contains("active") }) shouldBe false

        val (_, codeListing) = listStores(token, sessionCode)
        val (_, codeText) = toolResultText(codeListing)
        (codeText.lines().any { it.contains("lurelab") && it.contains("active") }) shouldBe true
        (codeText.lines().any { it.contains("velotrip") && it.contains("active") }) shouldBe false
    }

    @Test
    fun `create_redirect (MUTATION) is refused for a viewer with an active store selected, and the refusal is journaled`() {
        val storeId = registerStore()
        val subject = "viewer-create-redirect"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(token, sessionId, "create_redirect", mapOf("from_path" to "/a", "to_path" to "/b"))

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "access.role.insufficient"

        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val deniedEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "create_redirect" }
        checkNotNull(deniedEntry) { "no audit entry written for the role-insufficient refusal on create_redirect" }
        deniedEntry.outcome shouldBe "denied"
        deniedEntry.denialReason shouldBe "access.role.insufficient"
        deniedEntry.isMutation shouldBe true
    }

    @Test
    fun `create_redirect without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("velotrip-create-redirect")
        val subject = "operator-create-redirect-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(token, sessionId, "create_redirect", mapOf("from_path" to "/a", "to_path" to "/b"))

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "velotrip-create-redirect"
    }

    @Test
    fun `search_resources (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-search-resources"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(token, sessionId, "search_resources", mapOf("resource_type" to "collection"))

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `search_resources without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-search-resources")
        val subject = "operator-search-resources-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(token, sessionId, "search_resources", mapOf("resource_type" to "collection"))

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-search-resources"
    }

    @Test
    fun `get_seo (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-get-seo"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_seo",
            mapOf("resource_type" to "collection", "resource_id" to "gid://shopify/Collection/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `get_seo without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-get-seo")
        val subject = "operator-get-seo-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_seo",
            mapOf("resource_type" to "collection", "resource_id" to "gid://shopify/Collection/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-get-seo"
    }

    @Test
    fun `same authenticated session — native list_stores and relayed check_shopify_connection both succeed and are journaled with correct isMutation and storeId`() {
        val storeId = registerStore("velotrip-native-and-relayed")
        val subject = "operator-native-and-relayed"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        useStore(token, sessionId, "velotrip-native-and-relayed")

        val (listStoresResponse, listStoresPayload) = listStores(token, sessionId)
        listStoresResponse.statusCode shouldBe HttpStatus.OK
        val (listStoresIsError, _) = toolResultText(listStoresPayload)
        listStoresIsError shouldBe false

        val (checkConnectionResponse, checkConnectionPayload) = toolsCall(token, sessionId, "check_shopify_connection")
        checkConnectionResponse.statusCode shouldBe HttpStatus.OK
        val (checkConnectionIsError, checkConnectionText) = toolResultText(checkConnectionPayload)
        checkConnectionIsError shouldBe false
        checkConnectionText shouldContain "Connexion Shopify OK"

        val identityEntries = auditLogRepository.findByIdentity(identityId).shouldBeRight()
        val listStoresEntry = identityEntries.firstOrNull { it.toolName == "list_stores" }
        checkNotNull(listStoresEntry) { "no audit entry written for the native list_stores call" }
        listStoresEntry.outcome shouldBe "ok"
        listStoresEntry.isMutation shouldBe false

        val storeEntries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val checkConnectionEntry = storeEntries.firstOrNull { it.identityId == identityId && it.toolName == "check_shopify_connection" }
        checkNotNull(checkConnectionEntry) { "no audit entry written for the relayed check_shopify_connection call" }
        checkConnectionEntry.outcome shouldBe "ok"
        checkConnectionEntry.isMutation shouldBe false
        checkConnectionEntry.storeId shouldBe storeId
    }

    @Test
    fun `tools list exposes exactly the 80 real tools — 10 native and 70 relayed, no duplicate name`() {
        val storeId = registerStore()
        val subject = "operator-tools-list-count"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (listResponse, listPayload) = toolsList(token, sessionId)

        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        val names = tools.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }

        names.toSet() shouldHaveSize names.size
        names shouldHaveSize 80
        names shouldContain "list_stores"
        names shouldContain "use_store"
        names shouldContain "create_redirect"
        names shouldContain "search_resources"
        names shouldContain "list_menus"
        names shouldContain "get_seo"
        names shouldContain "list_metaobjects"
        names shouldContain "get_metaobject"
        names shouldContain "list_pages"
        names shouldContain "get_page_metafields"
        names shouldContain "check_shopify_connection"
        names shouldContain "publish_page"
        names shouldContain "unpublish_page"
    }

    @Test
    fun `tools list carries a non-empty description for all 80 tools, and no two tools share one — D30`() {
        val storeId = registerStore()
        val subject = "operator-tools-list-descriptions"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (listResponse, listPayload) = toolsList(token, sessionId)

        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        val descriptionsByName = tools.mapNotNull {
            val tool = it as? Map<*, *> ?: return@mapNotNull null
            (tool["name"] as? String) to (tool["description"] as? String)
        }.toMap()

        descriptionsByName.forEach { (name, description) ->
            checkNotNull(description) { "tool '$name' has no description" }
            description.isBlank() shouldBe false
        }
        descriptionsByName.values.toSet() shouldHaveSize descriptionsByName.size
    }

    @Test
    fun `tools list exposes, for every RELAIS tool, the real inputSchema from the LOT2-19 artifact — D30`() {
        val storeId = registerStore()
        val subject = "operator-tools-list-input-schemas"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (listResponse, listPayload) = toolsList(token, sessionId)

        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        val toolsByName = tools.mapNotNull { (it as? Map<*, *>) }
            .mapNotNull { tool -> (tool["name"] as? String)?.let { it to tool } }
            .toMap()

        val relayedNames = relayGateway.relayedTools().map { it.toolName }.filterNot { it in nativeToolNames.names }
        relayedNames shouldHaveSize 70

        relayedNames.forEach { toolName ->
            val exposedInputSchema = toolsByName[toolName]?.get("inputSchema")
            checkNotNull(exposedInputSchema) { "relayed tool '$toolName' missing from tools/list or has no inputSchema" }

            val contract = relayToolContracts.byToolName[toolName]
            checkNotNull(contract) { "no LOT2-19 contract for relayed tool '$toolName'" }
            val expectedInputSchema = objectMapper.readValue(contract.inputSchemaJson, Map::class.java)

            exposedInputSchema shouldBe expectedInputSchema
        }
    }

    @Test
    fun `list_metaobjects (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-list-metaobjects"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(token, sessionId, "list_metaobjects", emptyMap())

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `list_metaobjects without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-list-metaobjects")
        val subject = "operator-list-metaobjects-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(token, sessionId, "list_metaobjects", emptyMap())

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-list-metaobjects"
    }

    @Test
    fun `get_metaobject (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-get-metaobject"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_metaobject",
            mapOf("metaobject_id" to "gid://shopify/Metaobject/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `get_metaobject without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-get-metaobject")
        val subject = "operator-get-metaobject-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_metaobject",
            mapOf("metaobject_id" to "gid://shopify/Metaobject/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-get-metaobject"
    }

    @Test
    fun `list_pages (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-list-pages"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(token, sessionId, "list_pages", emptyMap())

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `list_pages without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-list-pages")
        val subject = "operator-list-pages-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(token, sessionId, "list_pages", emptyMap())

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-list-pages"
    }

    @Test
    fun `get_page_metafields (READ) is callable by a viewer with an active store selected — reaches business logic instead of being blocked by role`() {
        val storeId = registerStore()
        val subject = "viewer-get-page-metafields"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.VIEWER)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_page_metafields",
            mapOf("page_id" to "gid://shopify/Page/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldNotContain "access.role.insufficient"
        combined shouldContain "storeCredential.not.found"
    }

    @Test
    fun `get_page_metafields without an active store selection is refused, naming the available stores`() {
        val storeId = registerStore("lurelab-get-page-metafields")
        val subject = "operator-get-page-metafields-no-selection"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_page_metafields",
            mapOf("page_id" to "gid://shopify/Page/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, text) = toolResultText(callPayload)
        isError shouldBe true
        text shouldContain "store.selection.missing"
        text shouldContain "lurelab-get-page-metafields"
    }

    @Test
    fun `get_seo refuses a resource_id whose gid type does not match resource_type, before reaching Shopify — D27`() {
        val storeId = registerStore("velotrip-get-seo-wrong-type")
        val subject = "operator-get-seo-wrong-type"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_seo",
            mapOf("resource_type" to "collection", "resource_id" to "gid://shopify/Product/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldContain "resource_id invalide"
        combined shouldContain "gid://shopify/Product/1"
        combined shouldContain "Collection"
        combined shouldNotContain "introuvable"
        combined shouldNotContain "storeCredential.not.found"

        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        val entry = entries.firstOrNull { it.identityId == identityId && it.toolName == "get_seo" }
        checkNotNull(entry) { "no audit entry written for the refused call — the attempt must be journaled (D17)" }
    }

    @Test
    fun `get_seo refuses a syntactically invalid resource_id, before reaching Shopify — D27`() {
        val storeId = registerStore("velotrip-get-seo-malformed")
        val subject = "operator-get-seo-malformed"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_seo",
            mapOf("resource_type" to "product", "resource_id" to "not-a-gid"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldContain "resource_id invalide"
        combined shouldNotContain "storeCredential.not.found"
    }

    @Test
    fun `get_metaobject refuses a metaobject_id of the wrong gid type, before reaching Shopify — D27`() {
        val storeId = registerStore("velotrip-get-metaobject-wrong-type")
        val subject = "operator-get-metaobject-wrong-type"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_metaobject",
            mapOf("metaobject_id" to "gid://shopify/Product/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldContain "metaobject_id invalide"
        combined shouldContain "Metaobject"
        combined shouldNotContain "introuvable"
        combined shouldNotContain "storeCredential.not.found"
    }

    @Test
    fun `get_page_metafields refuses a page_id of the wrong gid type, before reaching Shopify — D27`() {
        val storeId = registerStore("velotrip-get-page-metafields-wrong-type")
        val subject = "operator-get-page-metafields-wrong-type"
        val identityId = resolveIdentity(subject)
        grant(identityId, storeId, GrantRole.OPERATOR)
        val token = jwt(subject)

        val sessionId = handshake(token)
        activeStoreExposedService.select(identityId, sessionId, storeId)

        val (callResponse, callPayload) = toolsCall(
            token,
            sessionId,
            "get_page_metafields",
            mapOf("page_id" to "gid://shopify/Article/1"),
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val (isError, texts) = toolResultTexts(callPayload)
        isError shouldBe true
        val combined = texts.joinToString("\n")
        combined shouldContain "page_id invalide"
        combined shouldContain "Page"
        combined shouldNotContain "introuvable"
        combined shouldNotContain "storeCredential.not.found"
    }
}
