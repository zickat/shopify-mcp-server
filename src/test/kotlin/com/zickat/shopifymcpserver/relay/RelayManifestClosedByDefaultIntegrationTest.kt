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
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.time.Instant
import java.util.Date
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
import org.yaml.snakeyaml.Yaml

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RelayManifestClosedByDefaultIntegrationTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val objectMapper = ObjectMapper()

    companion object {
        private const val EXPECTED_AUDIENCE = "https://shopify-mcp-server.test/mcp"
        private const val ISSUER_URI = "https://idp.test.local/"
        private const val REMOVED_TOOL = "search_products"

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
                        .setBody("""{"content":[{"type":"text","text":"should never be reached for the removed tool"}],"isError":false}""")
            }
            start()
        }

        private val reducedManifest = loadApplicationYmlManifest().filterNot { it.toolName == REMOVED_TOOL }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                jwksServer.url("/jwks").toString()
            }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { ISSUER_URI }
            registry.add("mcp.security.expected-audience") { EXPECTED_AUDIENCE }
            registry.add("relay.ts.base-url") { relayTsDouble.url("").toString().trimEnd('/') }
            reducedManifest.forEachIndexed { index, row ->
                registry.add("relay.manifest[$index].tool-name") { row.toolName }
                registry.add("relay.manifest[$index].route") { row.route }
                registry.add("relay.manifest[$index].kind") { row.kind }
            }
        }

        @JvmStatic
        @AfterAll
        fun shutdownDoubles() {
            jwksServer.shutdown()
            relayTsDouble.shutdown()
        }

        private data class ManifestRow(val toolName: String, val route: String, val kind: String)

        private fun loadApplicationYmlManifest(): List<ManifestRow> {
            val resourcesDir = File(System.getProperty("user.dir"), "src/main/resources/application.yml")
            val stream = resourcesDir.inputStream()
            val root = Yaml().load<Map<String, Any?>>(stream)
            @Suppress("UNCHECKED_CAST")
            val relay = root["relay"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val entries = relay["manifest"] as List<Map<String, Any?>>
            return entries.map { entry ->
                ManifestRow(
                    toolName = entry["tool-name"] as String,
                    route = entry["route"] as String,
                    kind = entry["kind"] as String,
                )
            }
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
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"lot2-07-closed-by-default-test","version":"0.0.0"}}}""",
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

    @Test
    fun `a tool removed from the manifest by mistake is refused, closed by default — real manifest minus search_products`() {
        val token = jwt("operator-closed-by-default")
        val sessionId = handshake(token)

        val (listResponse, listPayload) = sendJsonRpcRequestAndParseJsonPayload(
            """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
            token,
            sessionId,
        )
        listResponse.statusCode shouldBe HttpStatus.OK
        val tools = (listPayload?.get("result") as? Map<*, *>)?.get("tools") as? List<*>
        checkNotNull(tools) { "tools/list did not return a tools array: $listPayload" }
        val names = tools.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
        names shouldNotContain REMOVED_TOOL

        val (callResponse, callPayload) = sendJsonRpcRequestAndParseJsonPayload(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"$REMOVED_TOOL","arguments":{}}}""",
            token,
            sessionId,
        )

        callResponse.statusCode shouldBe HttpStatus.OK
        val errorNode = callPayload?.get("error") as? Map<*, *>
        checkNotNull(errorNode) { "removed tool '$REMOVED_TOOL' was not refused at the JSON-RPC layer — response was: $callPayload" }
        errorNode["message"] as? String shouldContain "Unknown tool"
        errorNode["data"] as? String shouldContain REMOVED_TOOL
    }
}
