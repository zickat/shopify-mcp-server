package com.zickat.shopifymcpserver.identity

import io.kotest.matchers.shouldBe
import java.util.function.Supplier
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext

class LoopbackOnlyAuthorizationManagerTest {

    private val manager = LoopbackOnlyAuthorizationManager()
    private val unusedAuthentication = Supplier<Authentication> { error("the loopback check never inspects the authentication") }

    private fun contextFor(remoteAddr: String): RequestAuthorizationContext {
        val request = MockHttpServletRequest()
        request.remoteAddr = remoteAddr
        return RequestAuthorizationContext(request)
    }

    @Test
    fun `a request from 127-0-0-1 is granted`() {
        val decision = manager.authorize(unusedAuthentication, contextFor("127.0.0.1")) as AuthorizationDecision

        decision.isGranted shouldBe true
    }

    @Test
    fun `a request from the IPv6 loopback address is granted`() {
        val decision = manager.authorize(unusedAuthentication, contextFor("::1")) as AuthorizationDecision

        decision.isGranted shouldBe true
    }

    @Test
    fun `a request from a public internet address is denied — never a public route for TS callbacks`() {
        val decision = manager.authorize(unusedAuthentication, contextFor("203.0.113.5")) as AuthorizationDecision

        decision.isGranted shouldBe false
    }

    @Test
    fun `a request from a near-loopback but distinct address is denied — no fuzzy matching`() {
        val decision = manager.authorize(unusedAuthentication, contextFor("127.0.0.2")) as AuthorizationDecision

        decision.isGranted shouldBe false
    }
}
