package com.zickat.shopifymcpserver

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Garde-fou Modulith posé par LOT0-02 : vérifie que les frontières entre modules (shared_kernel
 * OPEN, identity/tenancy/vault/audit CLOSED) sont respectées — jamais d'import direct entre
 * domaines. La CI de LOT0-09 s'appuiera dessus.
 */
class ModularityTests {

    private val modules = ApplicationModules.of(ShopifyMcpServerApplication::class.java)

    @Test
    fun `verifies module structure`() {
        modules.verify()
    }

    @Test
    fun `writes module documentation`() {
        // Non bloquant pour verify(), mais un utilitaire gratuit : produit un aperçu texte des
        // modules détectés dans target/spring-modulith-docs, utile pour relire le découpage.
        println(modules)
    }
}
