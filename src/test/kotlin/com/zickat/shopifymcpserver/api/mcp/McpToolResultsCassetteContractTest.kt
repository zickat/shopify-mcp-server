package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class McpToolResultsCassetteContractTest {

    @Serializable
    private data class ContentBlock(val type: String, val text: String)

    @Serializable
    private data class ToolOutput(val content: List<ContentBlock>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun expectedTexts(resourcePath: String): List<String> {
        val cassette = Cassette.fromClasspathResource(resourcePath)
        val output = json.decodeFromJsonElement(ToolOutput.serializer(), cassette.toolOutput)
        return output.content.map { it.text }
    }

    private fun actualTexts(result: CallToolResult): List<String> =
        result.content().map { (it as TextContent).text() }

    @Test
    fun `list_stores with no active selection reproduces the stores-ts cassette's toolOutput, bit for bit`() {
        val stores = listOf(
            GrantedStore(storeId = "velotrip-id", slug = "velotrip"),
            GrantedStore(storeId = "lurelab-id", slug = "lurelab"),
        )

        val result = McpToolResults.describeStores(stores, activeStoreId = null)

        actualTexts(result) shouldBe expectedTexts("cassettes/list-stores.json")
    }

    @Test
    fun `use_store on velotrip reproduces the stores-ts cassette's toolOutput, bit for bit`() {
        val result = McpToolResults.storeActivated("velotrip")

        actualTexts(result) shouldBe expectedTexts("cassettes/use-store-velotrip.json")
    }

    @Test
    fun `use_store on lurelab reproduces the stores-ts cassette's toolOutput, bit for bit`() {
        val result = McpToolResults.storeActivated("lurelab")

        actualTexts(result) shouldBe expectedTexts("cassettes/use-store-lurelab.json")
    }
}
