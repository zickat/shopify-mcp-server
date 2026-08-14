package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.menus.domain.models.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class MenuToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `errorResult should render the translated message under the store banner`() {
        val result = MenuToolResults.errorResult("velotrip", DomainError("menus.probe.error"))

        result.isError() shouldBe true
        texts(result)[0] shouldBe "Boutique : velotrip"
    }

    @Test
    fun `listMenusResult should report no menu found when there is no query`() {
        val result = MenuToolResults.listMenusResult("velotrip", ListMenusResult(hadQuery = false, blocks = emptyList(), truncated = false))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucun menu trouvé sur le store.")
        result.isError() shouldBe false
    }

    @Test
    fun `listMenusResult should report no menu found for this filter when there is a query`() {
        val result = MenuToolResults.listMenusResult("velotrip", ListMenusResult(hadQuery = true, blocks = emptyList(), truncated = false))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucun menu trouvé pour ce filtre.")
    }

    @Test
    fun `listMenusResult should join every block with a count header`() {
        val result = MenuToolResults.listMenusResult(
            "velotrip",
            ListMenusResult(hadQuery = false, blocks = listOf("▸ Main menu — id: gid://shopify/Menu/1", "▸ Footer — id: gid://shopify/Menu/2"), truncated = false),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "2 menu(s) trouvé(s).\n\n▸ Main menu — id: gid://shopify/Menu/1\n\n▸ Footer — id: gid://shopify/Menu/2",
        )
    }

    @Test
    fun `listMenusResult should append the truncation note when truncated`() {
        val result = MenuToolResults.listMenusResult(
            "velotrip",
            ListMenusResult(hadQuery = false, blocks = listOf("▸ Main menu — id: gid://shopify/Menu/1"), truncated = true),
        )

        texts(result)[1] shouldBe "1 menu(s) trouvé(s) (résultats tronqués à 500 — affiner la requête).\n\n▸ Main menu — id: gid://shopify/Menu/1"
    }
}
