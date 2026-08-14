package com.zickat.shopifymcpserver.relay

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
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.net.ServerSocket
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RelayRealTsProcessCrossoverTest : WithMongoDBContainer() {

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

    @Autowired
    private lateinit var storeCredentialUseCase: StoreCredentialUseCase

    @LocalServerPort
    private var localServerPort: Int = 0

    private val objectMapper = ObjectMapper()

    companion object {
        private const val EXPECTED_AUDIENCE = "https://shopify-mcp-server.test/mcp"
        private const val ISSUER_URI = "https://idp.test.local/"

        private val tsRepoRoot = File(System.getProperty("user.home"), "IA_sandbox/mcp-shopify-catalog")
        private val tsDistIndex = File(tsRepoRoot, "dist/index.js")
        private val credentialsFile = File(System.getProperty("user.home"), ".config/shopify-catalog/credentials.json")

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

        private val relayTsPort: Int = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                jwksServer.url("/jwks").toString()
            }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { ISSUER_URI }
            registry.add("mcp.security.expected-audience") { EXPECTED_AUDIENCE }
            registry.add("relay.ts.base-url") { "http://127.0.0.1:$relayTsPort" }
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

        private fun rpcHeaders(token: String, sessionId: String? = null): HttpHeaders {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.accept = listOf(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
            headers.setBearerAuth(token)
            sessionId?.let { headers.set("Mcp-Session-Id", it) }
            return headers
        }
    }

    private fun sendJsonRpcRequestAndParseJsonPayload(body: String, token: String, sessionId: String? = null): Pair<org.springframework.http.ResponseEntity<String>, Map<String, Any?>?> {
        val response = restTemplate.exchange("/mcp", HttpMethod.POST, HttpEntity(body, rpcHeaders(token, sessionId)), String::class.java)
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
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"lot2-07-real-crossover-test","version":"0.0.0"}}}""",
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

    private fun startRealTsRelayProcess(): Process {
        val builder = ProcessBuilder("node", "dist/index.js")
        builder.directory(tsRepoRoot)
        builder.environment()["RELAY_MODE"] = "true"
        builder.environment()["RELAY_TS_BASE_URL"] = "http://127.0.0.1:$relayTsPort"
        builder.environment()["RELAY_KOTLIN_BASE_URL"] = "http://127.0.0.1:$localServerPort"
        builder.environment().remove("SHOPIFY_API_KEY")
        builder.environment().remove("SHOPIFY_API_SECRET")
        builder.redirectErrorStream(true)
        val process = builder.start()

        val deadline = System.currentTimeMillis() + 15_000
        var ready = false
        val output = StringBuilder()
        process.inputStream.bufferedReader().let { reader ->
            while (System.currentTimeMillis() < deadline && process.isAlive) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    output.appendLine(line)
                    if (line.contains("Mode relayé actif")) {
                        ready = true
                        break
                    }
                } else {
                    Thread.sleep(50)
                }
            }
        }
        check(ready) { "real TS relay process did not report readiness within 15s — output so far:\n$output" }
        return process
    }

    private fun velotripCredentialPlaintext(): ByteArray {
        val root = objectMapper.readTree(credentialsFile)
        val velotrip = root.get("velotrip")
        val plaintext = objectMapper.createObjectNode()
        plaintext.put("apiKey", velotrip.get("SHOPIFY_API_KEY").asText())
        plaintext.put("apiSecret", velotrip.get("SHOPIFY_API_SECRET").asText())
        plaintext.put("shopDomain", velotrip.get("SHOPIFY_SHOP_DOMAIN").asText())
        return objectMapper.writeValueAsBytes(plaintext)
    }

    @Test
    fun `real Kotlin server and real TS relay process, wired together over loopback — check_shopify_connection round-trips to actual Shopify`() {
        assumeTrue(credentialsFile.isFile) { "no local Shopify credentials file at $credentialsFile — skipping the real cross-process proof" }
        assumeTrue(tsDistIndex.isFile) { "mcp-shopify-catalog dist/index.js not found at $tsDistIndex — skipping the real cross-process proof" }
        assumeTrue(System.getenv("CATALOG_MASTER_KEY") != null) { "no CATALOG_MASTER_KEY in the environment — skipping the real cross-process proof (storeCredentialUseCase.store needs a real master key to encrypt the real Shopify secret)" }

        val storeId = storeRepository.findBySlug("velotrip").fold(
            { storeRepository.save(StoreFixtures().withSlug("velotrip").build()).shouldBeRight().id.value },
            { existing -> existing.id.value },
        )
        storeCredentialUseCase.store(storeId, velotripCredentialPlaintext(), "read_products,read_content").shouldBeRight()

        val subject = "operator-real-crossover"
        val identityId = identityExposedService.resolve(ISSUER_URI, subject).shouldBeRight()
        grantRepository.save(
            Grant(
                id = GrantId(ObjectId().toHexString()),
                identityId = identityId,
                storeId = com.zickat.shopifymcpserver.tenancy.domain.models.StoreId(storeId),
                role = GrantRole.OPERATOR,
                grantedBy = identityId,
                createdAt = Clock.System.now(),
                expiresAt = Clock.System.now() + 1.hours,
                revokedAt = null,
            ),
        ).shouldBeRight()

        val tsProcess = startRealTsRelayProcess()
        try {
            val token = jwt(subject)
            val sessionId = handshake(token)

            val (useStoreResponse, useStorePayload) = sendJsonRpcRequestAndParseJsonPayload(
                """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"use_store","arguments":{"store_id":"$storeId"}}}""",
                token,
                sessionId,
            )
            useStoreResponse.statusCode shouldBe HttpStatus.OK
            val useStoreResult = useStorePayload?.get("result") as? Map<*, *>
            (useStoreResult?.get("isError") as? Boolean) shouldBe false

            val (callResponse, callPayload) = sendJsonRpcRequestAndParseJsonPayload(
                """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"check_shopify_connection","arguments":{}}}""",
                token,
                sessionId,
            )
            callResponse.statusCode shouldBe HttpStatus.OK
            val result = callPayload?.get("result") as? Map<*, *>
            checkNotNull(result) { "tools/call check_shopify_connection did not return a result: $callPayload" }
            val texts = (result["content"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
            val combinedText = texts.joinToString("\n")

            (result["isError"] as? Boolean) shouldBe false
            combinedText shouldContain "Connexion Shopify OK"

            val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
            val checkConnectionEntry = entries.firstOrNull { it.identityId == identityId && it.toolName == "check_shopify_connection" }
            checkNotNull(checkConnectionEntry) { "no audit entry for the real relayed check_shopify_connection call" }
            checkConnectionEntry.outcome shouldBe "ok"
            checkConnectionEntry.isMutation shouldBe false
        } finally {
            tsProcess.destroyForcibly()
            tsProcess.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
