package com.zickat.shopifymcpserver

import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModule
import org.springframework.modulith.core.ApplicationModules
import kotlin.streams.toList

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

    @Test
    fun `catalog domain modules depend on shared_kernel and shopify only — D23`() {
        CATALOG_DOMAIN_MODULES.forEach { moduleName ->
            forbiddenDependenciesOf(moduleName, CATALOG_DOMAIN_ALLOWED).shouldBeEmpty()
        }
    }

    @Test
    fun `cross-cutting view modules depend on shared_kernel, shopify and catalog domains only — D23`() {
        CROSS_CUTTING_VIEW_MODULES.forEach { moduleName ->
            forbiddenDependenciesOf(moduleName, CROSS_CUTTING_VIEW_ALLOWED).shouldBeEmpty()
        }
    }

    private fun forbiddenDependenciesOf(moduleName: String, allowed: Set<String>): List<String> {
        val module = moduleNamed(moduleName)
        return module.getDirectDependencies(modules).uniqueModules().toList()
            .map { it.identifier.toString() }
            .filterNot { it in allowed }
    }

    private fun moduleNamed(name: String): ApplicationModule =
        modules.getModuleByName(name).orElseThrow { AssertionError("D23 test setup: no such module '$name'") }

    companion object {
        private val CATALOG_DOMAIN_MODULES = setOf("redirects", "products")
        private val CROSS_CUTTING_VIEW_MODULES = setOf("catalog_status")

        private val CATALOG_DOMAIN_ALLOWED = setOf("shared_kernel", "shopify")
        private val CROSS_CUTTING_VIEW_ALLOWED = CATALOG_DOMAIN_ALLOWED + CATALOG_DOMAIN_MODULES
    }
}
