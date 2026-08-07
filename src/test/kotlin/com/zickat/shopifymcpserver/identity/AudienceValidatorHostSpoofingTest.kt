package com.zickat.shopifymcpserver.identity

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AudienceValidatorHostSpoofingTest : WithMongoDBContainer() {

    @LocalServerPort
    private var port: Int = 0

    companion object {
        private const val EXPECTED_AUDIENCE = "https://shopify-mcp-server.test/mcp"
        private const val ISSUER_URI = "https://idp.test.local/"
        private const val FORGED_HOST = "attacker.example"

        private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()

        private val jwksServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(JWKSet(signingKey.toPublicJWK()).toString())
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

        private fun jwtWithAudience(audience: String): String {
            val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build()
            val claims = JWTClaimsSet.Builder()
                .subject("operator-1")
                .issuer(ISSUER_URI)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build()
            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(RSASSASigner(signingKey))
            return signedJwt.serialize()
        }
    }

    private fun statusLineOfRawRequestWithForgedHost(host: String, token: String): String {
        Socket("127.0.0.1", port).use { socket ->
            val request = "GET /mcp HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Authorization: Bearer $token\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            return reader.readLine() ?: error("no response received from the embedded server")
        }
    }

    @Test
    fun `should reject a token whose audience matches the forged Host header but not the configured audience`() {
        val audienceForgedToMatchTheHostHeader = "http://$FORGED_HOST/mcp"
        val token = jwtWithAudience(audienceForgedToMatchTheHostHeader)

        val statusLine = statusLineOfRawRequestWithForgedHost(FORGED_HOST, token)

        statusLine.contains(" 401 ") shouldBe true
    }
}
