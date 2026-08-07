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

class VaultExposedServiceContractTest {

    private val theOneMethodAllowedToCarryDecryptedBytesOutOfVault = "resolveCredential"

    @Test
    fun `no method of VaultExposedService other than resolveCredential returns StoreCredential or a byte array, even wrapped in a generic type`() {
        val offending = VaultExposedService::class.java.declaredMethods
            .filterNot { method -> method.name == theOneMethodAllowedToCarryDecryptedBytesOutOfVault }
            .filter { method -> isSensitive(method) }

        offending shouldBe emptyList<Method>()
    }

    @Test
    fun `resolveCredential is the only door through which a decrypted secret is allowed to leave the vault module`() {
        val methodsCarryingDecryptedBytes = VaultExposedService::class.java.declaredMethods
            .filter { method -> isSensitive(method) }
            .map { method -> method.name }

        methodsCarryingDecryptedBytes shouldBe listOf(theOneMethodAllowedToCarryDecryptedBytesOutOfVault)
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
