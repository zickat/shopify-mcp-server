package com.zickat.shopifymcpserver.catalog_status.api.mcp

import com.zickat.shopifymcpserver.catalog_status.domain.models.ResourceSummary
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourcesResult
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class CatalogStatusToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `errorResult should render the translated message under the store banner`() {
        val result = CatalogStatusToolResults.errorResult("velotrip", DomainError("catalog_status.probe.error"))

        result.isError() shouldBe true
        texts(result)[0] shouldBe "Boutique : velotrip"
    }

    @Test
    fun `invalidResourceType should report the offending value with the expected values`() {
        val result = CatalogStatusToolResults.invalidResourceType("velotrip", "page")

        texts(result) shouldBe listOf("Boutique : velotrip", "Type de ressource invalide : \"page\" (attendu \"collection\" ou \"article\").")
        result.isError() shouldBe true
    }

    @Test
    fun `searchResourcesResult should report no collection found for this filter`() {
        val result = CatalogStatusToolResults.searchResourcesResult(
            "velotrip",
            SearchResourcesResult(SearchResourceType.COLLECTION, emptyList(), truncated = false),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucun(e) collection(s) trouvé(e) pour ce filtre.")
        result.isError() shouldBe false
    }

    @Test
    fun `searchResourcesResult should report no guide found for this filter`() {
        val result = CatalogStatusToolResults.searchResourcesResult(
            "velotrip",
            SearchResourcesResult(SearchResourceType.ARTICLE, emptyList(), truncated = false),
        )

        texts(result)[1] shouldBe "Aucun(e) guide(s) trouvé(e) pour ce filtre."
    }

    @Test
    fun `searchResourcesResult should list every resource with its pipeline status`() {
        val result = CatalogStatusToolResults.searchResourcesResult(
            "velotrip",
            SearchResourcesResult(
                SearchResourceType.COLLECTION,
                listOf(
                    ResourceSummary("gid://1", "First", "first", "to_review"),
                    ResourceSummary("gid://2", "Second", "second", "untreated"),
                ),
                truncated = false,
            ),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "2 collection(s) trouvé(e)(s).\n\n" +
                "- First (first) — statut pipeline : to_review — id: gid://1\n" +
                "- Second (second) — statut pipeline : untreated — id: gid://2",
        )
    }

    @Test
    fun `searchResourcesResult should append the truncation note when truncated`() {
        val result = CatalogStatusToolResults.searchResourcesResult(
            "velotrip",
            SearchResourcesResult(SearchResourceType.COLLECTION, listOf(ResourceSummary("gid://1", "First", "first", "untreated")), truncated = true),
        )

        texts(result)[1] shouldBe "1 collection(s) trouvé(e)(s) (résultats tronqués à 500 — affiner la requête).\n\n" +
            "- First (first) — statut pipeline : untreated — id: gid://1"
    }
}
