package com.zickat.shopifymcpserver.shopify.domain.models

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val plaintextJson = Json { ignoreUnknownKeys = true }

@Serializable
data class ShopifyCredential(
    @SerialName("apiKey") val apiKey: String,
    @SerialName("apiSecret") val apiSecret: String,
    @SerialName("shopDomain") val shopDomain: String,
) {
    fun toPlaintext(): ByteArray = plaintextJson.encodeToString(serializer(), this).toByteArray()

    companion object {
        fun fromPlaintext(plaintext: ByteArray): Either<UseCaseError, ShopifyCredential> =
            try {
                plaintextJson.decodeFromString(serializer(), plaintext.toString(Charsets.UTF_8)).right()
            } catch (_: SerializationException) {
                TechnicalError("shopify.credential.malformed").left()
            }
    }
}
