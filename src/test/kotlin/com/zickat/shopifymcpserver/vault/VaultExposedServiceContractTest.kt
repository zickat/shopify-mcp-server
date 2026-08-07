package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `schema.md` §3 / `LOT0-03` : « aucun `exposed_interface` ne retourne ce document ». Test de
 * structure par réflexion — la garantie doit tenir même si `LOT0-04` ajoute des méthodes à cette
 * interface : ce test échouera si l'une d'elles retourne `StoreCredential` ou un type sensible.
 */
class VaultExposedServiceContractTest {

    @Test
    fun `no method of VaultExposedService returns StoreCredential or a byte array`() {
        val offending = VaultExposedService::class.java.declaredMethods.filter { method ->
            StoreCredential::class.java.isAssignableFrom(method.returnType) ||
                method.returnType == ByteArray::class.java
        }

        offending shouldBe emptyList()
    }
}
