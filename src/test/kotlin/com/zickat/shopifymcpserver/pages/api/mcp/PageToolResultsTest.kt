package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.pages.domain.CreatePageOutcome
import com.zickat.shopifymcpserver.pages.domain.CreatePageResult
import com.zickat.shopifymcpserver.pages.domain.DeletePageOutcome
import com.zickat.shopifymcpserver.pages.domain.DeletePageResult
import com.zickat.shopifymcpserver.pages.domain.GetPageMetafieldsOutcome
import com.zickat.shopifymcpserver.pages.domain.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.domain.ListPagesResult
import com.zickat.shopifymcpserver.pages.domain.PageMetafield
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishOutcome
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishResult
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsOutcome
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsResult
import com.zickat.shopifymcpserver.pages.domain.UpdatePageOutcome
import com.zickat.shopifymcpserver.pages.domain.UpdatePageResult
import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class PageToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `listPagesResult should report no page found on an empty store without query`() {
        val result = PageToolResults.listPagesResult("velotrip", ListPagesResult(emptyList(), truncated = false, hadQuery = false))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucune page trouvée sur le store.")
    }

    @Test
    fun `listPagesResult should report no page found for a filter when hadQuery is true`() {
        val result = PageToolResults.listPagesResult("velotrip", ListPagesResult(emptyList(), truncated = false, hadQuery = true))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucune page trouvée pour ce filtre.")
    }

    @Test
    fun `listPagesResult should list every page found`() {
        val result = PageToolResults.listPagesResult(
            "velotrip",
            ListPagesResult(listOf(PageSummary("gid://shopify/Page/1", "Contact", "contact")), truncated = false, hadQuery = false),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "1 page(s) trouvée(s).\n\n- Contact (contact) — id: gid://shopify/Page/1")
    }

    @Test
    fun `listPagesResult should report a truncated listing with the truncation note`() {
        val pages = (1..10).map { PageSummary("gid://shopify/Page/$it", "Page $it", "page-$it") }
        val result = PageToolResults.listPagesResult("velotrip", ListPagesResult(pages, truncated = true, hadQuery = false))

        texts(result)[1].startsWith("10 page(s) trouvée(s) (résultats tronqués à 500 — affiner la requête).") shouldBe true
    }

    @Test
    fun `getPageMetafieldsResult should report page not found`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(GetPageMetafieldsOutcome.NOT_FOUND, pageId = "gid://shopify/Page/999"),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page introuvable : gid://shopify/Page/999")
        result.isError() shouldBe true
    }

    @Test
    fun `getPageMetafieldsResult should report no metafield defined when the page has none`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(GetPageMetafieldsOutcome.FOUND, title = "Contact", metafields = emptyList(), requestedKeys = null, truncated = false),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"Contact\" — aucun metafield custom.* défini.")
    }

    @Test
    fun `getPageMetafieldsResult should list every custom metafield found without keys`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(
                GetPageMetafieldsOutcome.FOUND,
                title = "Guides",
                metafields = listOf(PageMetafield("summary", "single_line_text_field", "Résumé")),
                requestedKeys = null,
                truncated = false,
            ),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"Guides\" — 1 metafield(s) custom.* :\n- summary: Résumé (single_line_text_field)")
    }

    @Test
    fun `getPageMetafieldsResult should filter to the requested keys and report the absent one as non defini`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(
                GetPageMetafieldsOutcome.FOUND,
                title = "Guides",
                metafields = listOf(
                    PageMetafield("summary", "single_line_text_field", "Résumé"),
                    PageMetafield("themes", "list.metaobject_reference", "[]"),
                ),
                requestedKeys = listOf("themes", "nonexistent_key"),
                truncated = false,
            ),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Page \"Guides\" — 1 metafield(s) custom.* parmi les clés demandées :\n" +
                "- themes: [] (list.metaobject_reference)\n" +
                "Non défini : nonexistent_key",
        )
    }

    @Test
    fun `getPageMetafieldsResult should report every key as absent when none of them is defined`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(GetPageMetafieldsOutcome.FOUND, title = "Guides", metafields = emptyList(), requestedKeys = listOf("themes"), truncated = false),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Page \"Guides\" — 0 metafield(s) custom.* parmi les clés demandées :\n" +
                "  (aucune des clés demandées n'est définie)\n" +
                "Non défini : themes",
        )
    }

    @Test
    fun `getPageMetafieldsResult should flag a truncated metafields page explicitly`() {
        val result = PageToolResults.getPageMetafieldsResult(
            "velotrip",
            GetPageMetafieldsResult(
                GetPageMetafieldsOutcome.FOUND,
                title = "Guides",
                metafields = listOf(PageMetafield("summary", "single_line_text_field", "Résumé")),
                requestedKeys = null,
                truncated = true,
            ),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Page \"Guides\" — 1 metafield(s) custom.* :\n- summary: Résumé (single_line_text_field)" +
                "\n\n⚠️ Lecture des metafields tronquée — d'autres metafields custom.* peuvent exister au-delà de la borne.",
        )
    }

    @Test
    fun `createPageResult should report the created draft with the draft note`() {
        val result = PageToolResults.createPageResult(
            "velotrip",
            CreatePageResult(CreatePageOutcome.CREATED, title = "T", handle = "t", pageId = "gid://shopify/Page/1", isPublished = false),
        )

        val text = texts(result)[1]
        text shouldBe "Page \"T\" créée (t, gid://shopify/Page/1) — isPublished: false.\n" +
            "Créée en brouillon — utiliser publish_page après relecture pour la mettre en ligne."
    }

    @Test
    fun `createPageResult should report the created published page without the draft note`() {
        val result = PageToolResults.createPageResult(
            "velotrip",
            CreatePageResult(CreatePageOutcome.CREATED, title = "T", handle = "t", pageId = "gid://shopify/Page/1", isPublished = true),
        )

        texts(result)[1] shouldBe "Page \"T\" créée (t, gid://shopify/Page/1) — isPublished: true."
    }

    @Test
    fun `createPageResult should report a failure`() {
        val result = PageToolResults.createPageResult(
            "velotrip",
            CreatePageResult(CreatePageOutcome.FAILED, title = "T", failureDetail = "can't be blank"),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Échec de la création de la Page \"T\" : can't be blank")
        result.isError() shouldBe true
    }

    @Test
    fun `updatePageResult should report a no-op`() {
        val result = PageToolResults.updatePageResult("velotrip", UpdatePageResult(UpdatePageOutcome.NO_OP))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucun champ fourni — aucune modification demandée.")
    }

    @Test
    fun `updatePageResult should report which fields changed with the effective handle note`() {
        val result = PageToolResults.updatePageResult(
            "velotrip",
            UpdatePageResult(
                UpdatePageOutcome.UPDATED,
                title = "New title",
                pageId = "gid://shopify/Page/1",
                changedFields = listOf("title", "handle"),
                effectiveHandle = "new-handle",
            ),
        )

        texts(result)[1] shouldBe "Page \"New title\" (gid://shopify/Page/1) mise à jour — champs modifiés : title, handle.\n" +
            "Handle effectif : new-handle"
    }

    @Test
    fun `updatePageResult should report which fields changed without a handle note when handle was not requested`() {
        val result = PageToolResults.updatePageResult(
            "velotrip",
            UpdatePageResult(UpdatePageOutcome.UPDATED, title = "New title", pageId = "gid://shopify/Page/1", changedFields = listOf("title")),
        )

        texts(result)[1] shouldBe "Page \"New title\" (gid://shopify/Page/1) mise à jour — champs modifiés : title."
    }

    @Test
    fun `updatePageResult should report page not found`() {
        val result = PageToolResults.updatePageResult("velotrip", UpdatePageResult(UpdatePageOutcome.NOT_FOUND, pageId = "gid://shopify/Page/404"))

        result.isError() shouldBe true
    }

    @Test
    fun `updatePageResult should report a failure`() {
        val result = PageToolResults.updatePageResult(
            "velotrip",
            UpdatePageResult(UpdatePageOutcome.FAILED, pageId = "gid://shopify/Page/1", failureDetail = "already taken"),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Échec de la mise à jour de la Page \"gid://shopify/Page/1\" : already taken")
    }

    @Test
    fun `togglePagePublishResult should report a publication`() {
        val result = PageToolResults.togglePagePublishResult(
            "velotrip",
            TogglePagePublishResult(TogglePagePublishOutcome.TOGGLED, title = "T", targetPublished = true, isPublished = true),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"T\" publiée : isPublished=true.")
    }

    @Test
    fun `togglePagePublishResult should report an unpublication`() {
        val result = PageToolResults.togglePagePublishResult(
            "velotrip",
            TogglePagePublishResult(TogglePagePublishOutcome.TOGGLED, title = "T", targetPublished = false, isPublished = false),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"T\" dépubliée : isPublished=false.")
    }

    @Test
    fun `togglePagePublishResult should report a no-op when already published`() {
        val result = PageToolResults.togglePagePublishResult(
            "velotrip",
            TogglePagePublishResult(TogglePagePublishOutcome.NO_OP, title = "T", targetPublished = true, isPublished = true),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"T\" : déjà publiée (isPublished: true) — aucune action (no-op).")
    }

    @Test
    fun `togglePagePublishResult should report a no-op when already unpublished`() {
        val result = PageToolResults.togglePagePublishResult(
            "velotrip",
            TogglePagePublishResult(TogglePagePublishOutcome.NO_OP, title = "T", targetPublished = false, isPublished = false),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Page \"T\" : déjà dépubliée (brouillon) (isPublished: false) — aucune action (no-op).")
    }

    @Test
    fun `togglePagePublishResult should report page not found`() {
        val result = PageToolResults.togglePagePublishResult("velotrip", TogglePagePublishResult(TogglePagePublishOutcome.NOT_FOUND, pageId = "gid://shopify/Page/404"))

        result.isError() shouldBe true
    }

    @Test
    fun `togglePagePublishResult should report a failure`() {
        val result = PageToolResults.togglePagePublishResult(
            "velotrip",
            TogglePagePublishResult(TogglePagePublishOutcome.FAILED, pageId = "gid://shopify/Page/1", failureDetail = "access denied"),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Échec de la publication/dépublication de la Page gid://shopify/Page/1 : access denied")
    }

    @Test
    fun `updatePageMetafieldsResult should report what was written`() {
        val result = PageToolResults.updatePageMetafieldsResult(
            "velotrip",
            UpdatePageMetafieldsResult(
                UpdatePageMetafieldsOutcome.UPDATED,
                title = "Guides",
                metafields = listOf(PageMetafieldInput("summary", "single_line_text_field", "Résumé")),
            ),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Page \"Guides\" mise à jour — 1 metafield(s) custom.* écrit(s) :\n- summary: Résumé (single_line_text_field)",
        )
    }

    @Test
    fun `updatePageMetafieldsResult should report page not found`() {
        val result = PageToolResults.updatePageMetafieldsResult("velotrip", UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.NOT_FOUND, pageId = "gid://shopify/Page/404"))

        result.isError() shouldBe true
    }

    @Test
    fun `updatePageMetafieldsResult should report a failure`() {
        val result = PageToolResults.updatePageMetafieldsResult(
            "velotrip",
            UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.FAILED, pageId = "gid://shopify/Page/1", failureDetail = "value : invalid"),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Échec de la mise à jour des metafields de la Page gid://shopify/Page/1 : value : invalid",
        )
    }

    @Test
    fun `deletePageResult should report the deletion with title and handle`() {
        val result = PageToolResults.deletePageResult("velotrip", DeletePageResult(DeletePageOutcome.DELETED, title = "Contact", handle = "contact"))

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Page \"Contact\" (contact) supprimée définitivement. Action irréversible (aucune restauration côté MCP).",
        )
    }

    @Test
    fun `deletePageResult should report page not found`() {
        val result = PageToolResults.deletePageResult("velotrip", DeletePageResult(DeletePageOutcome.NOT_FOUND, pageId = "gid://shopify/Page/404"))

        result.isError() shouldBe true
    }

    @Test
    fun `deletePageResult should report a failure`() {
        val result = PageToolResults.deletePageResult(
            "velotrip",
            DeletePageResult(DeletePageOutcome.FAILED, pageId = "gid://shopify/Page/1", failureDetail = "access denied"),
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Échec de la suppression de la Page gid://shopify/Page/1 : access denied")
    }
}
