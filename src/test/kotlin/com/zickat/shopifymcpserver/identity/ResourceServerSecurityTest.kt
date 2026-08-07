package com.zickat.shopifymcpserver.identity

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.Date

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ResourceServerSecurityTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    companion object {
        private const val EXPECTED_AUDIENCE = "https://shopify-mcp-server.test/mcp"
        private const val ISSUER_URI = "https://idp.test.local/"

        private val correctKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key-correct").generate()
        private val otherKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key-other").generate()

        private val jwksServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = publicJwksWithOnlyTheCorrectKeyPublished()
            }
            start()
        }

        private fun publicJwksWithOnlyTheCorrectKeyPublished(): MockResponse =
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(JWKSet(correctKey.toPublicJWK()).toString())

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

        private fun jwt(
            key: RSAKey,
            audience: List<String> = listOf(EXPECTED_AUDIENCE),
            expiresAt: Instant = Instant.now().plusSeconds(300),
            issuedAt: Instant = Instant.now().minusSeconds(60),
        ): String {
            val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build()
            val claims = JWTClaimsSet.Builder()
                .subject("operator-1")
                .issuer(ISSUER_URI)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build()
            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(RSASSASigner(key))
            return signedJwt.serialize()
        }

        private fun bearer(token: String): HttpEntity<Void> {
            val headers = HttpHeaders()
            headers.setBearerAuth(token)
            return HttpEntity(null, headers)
        }
    }

    @Test
    fun `should reject a request with no token with 401 and WWW-Authenticate, no data in the body`() {
        val response = restTemplate.exchange("/mcp", HttpMethod.GET, HttpEntity.EMPTY, String::class.java)

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE)?.contains("Bearer") shouldBe true
        (response.body.isNullOrEmpty()) shouldBe true
    }

    @Test
    fun `should reject an expired token with 401`() {
        val expired = jwt(correctKey, expiresAt = Instant.now().minusSeconds(60))

        val response = restTemplate.exchange("/mcp", HttpMethod.GET, bearer(expired), String::class.java)

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `should reject a token signed by a key absent from the configured JWKS with 401`() {
        val signedByUnknownKey = jwt(otherKey)

        val response = restTemplate.exchange("/mcp", HttpMethod.GET, bearer(signedByUnknownKey), String::class.java)

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `should reject a valid token whose audience is not this server — the most important test of this task`() {
        val wrongAudience = jwt(correctKey, audience = listOf("https://some-other-app.example/api"))

        val response = restTemplate.exchange("/mcp", HttpMethod.GET, bearer(wrongAudience), String::class.java)

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `should let a valid token with the correct audience pass the security filter, without asserting on downstream tenancy which LOT0-08 covers`() {
        val valid = jwt(correctKey, audience = listOf(EXPECTED_AUDIENCE))

        val response = restTemplate.exchange("/mcp", HttpMethod.GET, bearer(valid), String::class.java)

        (response.statusCode == HttpStatus.UNAUTHORIZED) shouldBe false
        (response.statusCode == HttpStatus.FORBIDDEN) shouldBe false
        response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE) shouldBe null
    }

    @Test
    fun `should serve the Protected Resource Metadata endpoint without any token`() {
        val response = restTemplate.getForEntity(
            "/.well-known/oauth-protected-resource/mcp",
            String::class.java,
        )

        response.statusCode shouldBe HttpStatus.OK
        (response.body?.contains("\"resource\"") == true) shouldBe true
        (response.body?.contains(ISSUER_URI) == true) shouldBe true
    }

    @Test
    fun `should keep actuator health public`() {
        val response = restTemplate.getForEntity("/actuator/health", String::class.java)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `should reject an unmapped path with 401, not a 404 that would leak the absence of security`() {
        val response = restTemplate.exchange(
            "/some-endpoint-that-does-not-exist",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String::class.java,
        )

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }
}
