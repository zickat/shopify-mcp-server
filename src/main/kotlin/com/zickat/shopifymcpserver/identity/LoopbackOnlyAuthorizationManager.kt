package com.zickat.shopifymcpserver.identity

import java.util.function.Supplier
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.util.matcher.IpAddressMatcher

class LoopbackOnlyAuthorizationManager : AuthorizationManager<RequestAuthorizationContext> {

    private val loopbackMatchers = listOf(IpAddressMatcher("127.0.0.1"), IpAddressMatcher("::1"))

    override fun authorize(authentication: Supplier<out Authentication>, context: RequestAuthorizationContext): AuthorizationResult =
        AuthorizationDecision(loopbackMatchers.any { it.matches(context.request) })
}
