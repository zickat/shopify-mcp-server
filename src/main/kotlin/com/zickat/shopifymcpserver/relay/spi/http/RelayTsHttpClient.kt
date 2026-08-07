package com.zickat.shopifymcpserver.relay.spi.http

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.relay.RelayProperties
import com.zickat.shopifymcpserver.relay.domain.models.RelayContentBlock
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayToolInvocationRequest
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayTsClient
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class RelayTsHttpClient(
    restClientBuilder: RestClient.Builder,
    properties: RelayProperties,
) : RelayTsClient {

    private val restClient = restClientBuilder.build()
    private val baseUrl = properties.ts.baseUrl
    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(request: RelayToolInvocationRequest): Either<UseCaseError, RelayToolOutcome> {
        val requestBody = json.encodeToString(
            RelayInvokeRequestWire.serializer(),
            RelayInvokeRequestWire(request.toolName, request.toolInput, request.storeId, request.role),
        )

        val responseBody: String? = try {
            restClient.post()
                .uri("$baseUrl/relay/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody.toByteArray(Charsets.UTF_8))
                .retrieve()
                .onStatus({ status -> status.isError }, { _, response -> throw RelayTsHttpStatusException(response.statusCode.value()) })
                .body(String::class.java)
        } catch (e: RelayTsHttpStatusException) {
            return TechnicalError("relay.ts.http.rejected", mapOf("status" to e.status.toString())).left()
        } catch (e: RestClientException) {
            return TechnicalError("relay.ts.network.failed").left()
        }

        val wire = try {
            json.decodeFromString(RelayInvokeResponseWire.serializer(), responseBody.orEmpty())
        } catch (e: SerializationException) {
            return TechnicalError("relay.ts.response.malformed").left()
        }

        return RelayToolOutcome(
            content = wire.content.map { RelayContentBlock(it.text) },
            isError = wire.isError,
        ).right()
    }
}

private class RelayTsHttpStatusException(val status: Int) : RuntimeException()

@Serializable
internal data class RelayInvokeRequestWire(
    val toolName: String,
    val toolInput: JsonElement,
    val storeId: String,
    val role: String,
)

@Serializable
internal data class RelayInvokeResponseWire(
    val content: List<RelayContentBlockWire>,
    val isError: Boolean = false,
)

@Serializable
internal data class RelayContentBlockWire(
    val type: String = "text",
    val text: String,
)
