package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import io.kotest.matchers.shouldBe
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import org.junit.jupiter.api.Test

/**
 * `schema.md` §3 / `LOT0-03` : « aucun `exposed_interface` ne retourne ce document ». Test de
 * structure par réflexion — la garantie doit tenir même si un futur lot ajoute des méthodes à
 * cette interface : ce test échouera si l'une d'elles retourne `StoreCredential`, un `ByteArray`,
 * ou l'un des deux **enveloppé** dans un type générique (`List<StoreCredential>`,
 * `Either<UseCaseError, StoreCredential>`, `Optional<ByteArray>`…).
 *
 * **Renforcé par `LOT0-04`** (le use case y ajoute `reveal(...): Either<UseCaseError, ByteArray>`,
 * mais sur `StoreCredentialUseCase`, jamais sur cette interface) : la version `LOT0-03` ne
 * regardait que le type de retour brut (`method.returnType`), qui subit l'effacement de type — une
 * méthode mal écrite retournant `List<StoreCredential>` aurait un `returnType` égal à `List.class`
 * et serait passée inaperçue. Cette version inspecte aussi `genericReturnType`, récursivement dans
 * les paramètres de type, pour attraper ce cas précis.
 */
class VaultExposedServiceContractTest {

    @Test
    fun `no method of VaultExposedService returns StoreCredential or a byte array, even wrapped in a generic type`() {
        val offending = VaultExposedService::class.java.declaredMethods.filter { method -> isSensitive(method) }

        offending shouldBe emptyList<Method>()
    }

    private fun isSensitive(method: Method): Boolean = containsSensitiveType(method.genericReturnType)

    private fun containsSensitiveType(type: Type): Boolean = when (type) {
        is Class<*> -> StoreCredential::class.java.isAssignableFrom(type) || type == ByteArray::class.java
        is ParameterizedType -> containsSensitiveType(type.rawType) || type.actualTypeArguments.any { containsSensitiveType(it) }
        is GenericArrayType -> containsSensitiveType(type.genericComponentType)
        is WildcardType -> (type.upperBounds + type.lowerBounds).any { containsSensitiveType(it) }
        else -> false
    }
}
