package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val contractsJson = Json { ignoreUnknownKeys = true }

data class RelayToolContract(val description: String, val inputSchemaJson: String)

@Component
class RelayToolContracts {

    val byToolName: Map<String, RelayToolContract> = loadContracts()

    private fun loadContracts(): Map<String, RelayToolContract> {
        val stream = requireNotNull(RelayToolContracts::class.java.classLoader.getResourceAsStream(CONTRACTS_RESOURCE_PATH)) {
            "no relay tool contracts artifact found at classpath:$CONTRACTS_RESOURCE_PATH — regenerate it (LOT2-19)"
        }
        val root = contractsJson.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
        return root.mapValues { (_, contract) ->
            val entry = contract.jsonObject
            RelayToolContract(
                description = entry.getValue("description").jsonPrimitive.content,
                inputSchemaJson = entry.getValue("inputSchema").toString(),
            )
        }
    }

    companion object {
        private const val CONTRACTS_RESOURCE_PATH = "relay/tool-contracts.json"
    }
}
