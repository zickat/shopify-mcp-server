package com.zickat.shopifymcpserver.seo.api.mcp

import com.zickat.shopifymcpserver.seo.domain.GetSeoResult
import com.zickat.shopifymcpserver.seo.domain.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class SeoToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `errorResult should render the translated message under the store banner`() {
        val result = SeoToolResults.errorResult("velotrip", DomainError("seo.resource_type.unexpected", mapOf("value" to "invoice")))

        result.isError() shouldBe true
        texts(result)[0] shouldBe "Boutique : velotrip"
    }

    @Test
    fun `invalidGidType should report the expected gid shape`() {
        val result = SeoToolResults.invalidGidType("velotrip", "resource_id", "gid://shopify/Order/1", "Product")

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "resource_id invalide : \"gid://shopify/Order/1\" n'est pas un identifiant Product (attendu gid://shopify/Product/...).",
        )
        result.isError() shouldBe true
    }

    @Test
    fun `invalidSeoResourceType should list the accepted resource types`() {
        val result = SeoToolResults.invalidSeoResourceType("velotrip", "invoice")

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Type de ressource invalide : \"invoice\" (attendu \"product\", \"collection\", \"article\" ou \"page\").",
        )
        result.isError() shouldBe true
    }

    @Test
    fun `getSeoResult should report resource not found`() {
        val result = SeoToolResults.getSeoResult("velotrip", "collection", "gid://shopify/Collection/999", GetSeoResult.NotFound)

        texts(result) shouldBe listOf("Boutique : velotrip", "Ressource collection introuvable : gid://shopify/Collection/999")
        result.isError() shouldBe true
    }

    @Test
    fun `getSeoResult should report both fields defined`() {
        val result = SeoToolResults.getSeoResult(
            "velotrip",
            "product",
            "gid://shopify/Product/1",
            GetSeoResult.found("Selle", "Titre SEO", "Description SEO"),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Ressource product \"Selle\" — SEO :\n- meta title : \"Titre SEO\"\n- meta description : \"Description SEO\"",
        )
    }

    @Test
    fun `getSeoResult should report an undefined field as non defini`() {
        val result = SeoToolResults.getSeoResult("velotrip", "article", "gid://shopify/Article/1", GetSeoResult.found("Guide", null, null))

        texts(result)[1] shouldBe "Ressource article \"Guide\" — SEO :\n- meta title : non défini\n- meta description : non défini"
    }

    @Test
    fun `updateSeoResult should report resource not found`() {
        val result = SeoToolResults.updateSeoResult("velotrip", "page", "gid://shopify/Page/404", UpdateSeoResult.NotFound)

        texts(result) shouldBe listOf("Boutique : velotrip", "Ressource page introuvable : gid://shopify/Page/404")
        result.isError() shouldBe true
    }

    @Test
    fun `updateSeoResult should report a no-op when no field was provided`() {
        val result = SeoToolResults.updateSeoResult("velotrip", "product", "gid://shopify/Product/1", UpdateSeoResult.NoOp)

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Ressource product (gid://shopify/Product/1) : aucun champ SEO fourni — aucune modification (no-op).",
        )
    }

    @Test
    fun `updateSeoResult should report a failure`() {
        val result = SeoToolResults.updateSeoResult("velotrip", "product", "gid://shopify/Product/1", UpdateSeoResult.failed("title : too long"))

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Échec de la mise à jour SEO de product (gid://shopify/Product/1) : title : too long",
        )
        result.isError() shouldBe true
    }

    @Test
    fun `updateSeoResult should report both fields modified`() {
        val result = SeoToolResults.updateSeoResult(
            "velotrip",
            "collection",
            "gid://shopify/Collection/1",
            UpdateSeoResult.updated(
                title = "Vélos",
                finalMetaTitle = "Nouveau titre",
                finalMetaDescription = "Nouvelle description",
                titleModified = true,
                descriptionModified = true,
            ),
        )

        texts(result)[1] shouldBe "Ressource collection \"Vélos\" — SEO mis à jour :\n" +
            "- meta title : \"Nouveau titre\"  (modifié)\n" +
            "- meta description : \"Nouvelle description\"  (modifié)\n" +
            "(content_status non modifié — une retouche SEO ne remet pas la fiche à review.)"
    }

    @Test
    fun `updateSeoResult should report the untouched field as inchange`() {
        val result = SeoToolResults.updateSeoResult(
            "velotrip",
            "collection",
            "gid://shopify/Collection/1",
            UpdateSeoResult.updated(
                title = "Vélos",
                finalMetaTitle = "Nouveau titre",
                finalMetaDescription = null,
                titleModified = true,
                descriptionModified = false,
            ),
        )

        texts(result)[1] shouldBe "Ressource collection \"Vélos\" — SEO mis à jour :\n" +
            "- meta title : \"Nouveau titre\"  (modifié)\n" +
            "- meta description : non défini  (inchangé)\n" +
            "(content_status non modifié — une retouche SEO ne remet pas la fiche à review.)"
    }
}
