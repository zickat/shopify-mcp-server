package com.zickat.shopifymcpserver

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTests {

    private val modules = ApplicationModules.of(ShopifyMcpServerApplication::class.java)

    @Test
    fun `verifies module structure`() {
        modules.verify()
    }

    @Test
    fun `writes module documentation`() {
        println(modules)
    }
}
