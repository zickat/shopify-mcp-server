package com.zickat.shopifymcpserver.metaobjects.api.mcp

import com.zickat.shopifymcpserver.metaobjects.domain.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.ListMetaobjectsResult
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectInstanceWithReferences
import com.zickat.shopifymcpserver.metaobjects.domain.UpdateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionSummary
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class MetaobjectToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `listMetaobjectsResult should report no definition found on an empty store`() {
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Definitions(emptyList(), truncated = false))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucune définition de metaobject trouvée sur le store.")
    }

    @Test
    fun `listMetaobjectsResult should list every definition with its instance count and the how-to-drill-down hint`() {
        val definitions = listOf(
            MetaobjectDefinitionSummary("faq_item", "FAQ Item", 3),
            MetaobjectDefinitionSummary("guide_theme", "Guide Theme", 1),
        )
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Definitions(definitions, truncated = false))

        texts(result)[1] shouldBe "2 type(s) de metaobject trouvé(s) sur le store :\n" +
            "- faq_item (FAQ Item) : 3 instance(s)\n" +
            "- guide_theme (Guide Theme) : 1 instance(s)\n\n" +
            "Appeler list_metaobjects(type: \"...\") pour lister les instances d'un type."
    }

    @Test
    fun `listMetaobjectsResult should flag a truncated definitions page explicitly`() {
        val definitions = listOf(MetaobjectDefinitionSummary("faq_item", "FAQ Item", 3))
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Definitions(definitions, truncated = true))

        texts(result)[1] shouldBe "1 type(s) de metaobject trouvé(s) sur le store :\n" +
            "- faq_item (FAQ Item) : 3 instance(s)\n\n" +
            "Appeler list_metaobjects(type: \"...\") pour lister les instances d'un type.\n\n" +
            "⚠️ Balayage des définitions tronqué à 50 — d'autres types peuvent exister au-delà de cette borne."
    }

    @Test
    fun `listMetaobjectsResult should report no instance found for a type`() {
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Instances("faq_item", emptyList(), truncated = false))

        texts(result) shouldBe listOf("Boutique : velotrip", "Aucun metaobject de type \"faq_item\" trouvé.")
    }

    @Test
    fun `listMetaobjectsResult should list every instance with its fields and reference status`() {
        val instances = listOf(
            MetaobjectInstanceWithReferences("gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("title", "T")), MetaobjectReferenceStatus.Orphan),
        )
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Instances("faq_item", instances, truncated = false))

        texts(result)[1] shouldBe "1 instance(s) de type \"faq_item\" :\n\n" +
            "gid://shopify/Metaobject/1 — orphelin (aucune référence entrante trouvée)\n" +
            "  - title: T"
    }

    @Test
    fun `listMetaobjectsResult should render a null field value as vide`() {
        val instances = listOf(
            MetaobjectInstanceWithReferences("gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("articles", null)), MetaobjectReferenceStatus.Orphan),
        )
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Instances("faq_item", instances, truncated = false))

        texts(result)[1] shouldBe "1 instance(s) de type \"faq_item\" :\n\n" +
            "gid://shopify/Metaobject/1 — orphelin (aucune référence entrante trouvée)\n" +
            "  - articles: (vide)"
    }

    @Test
    fun `listMetaobjectsResult should flag a truncated instance scan explicitly`() {
        val instances = listOf(
            MetaobjectInstanceWithReferences("gid://shopify/Metaobject/1", emptyList(), MetaobjectReferenceStatus.Orphan),
        )
        val result = MetaobjectToolResults.listMetaobjectsResult("velotrip", ListMetaobjectsResult.Instances("faq_item", instances, truncated = true))

        texts(result)[1] shouldBe "1 instance(s) de type \"faq_item\" :\n\n" +
            "gid://shopify/Metaobject/1 — orphelin (aucune référence entrante trouvée)\n" +
            "  (aucun champ)\n\n" +
            "⚠️ Balayage tronqué à 500 instances — d'autres instances de ce type peuvent exister au-delà de cette borne."
    }

    @Test
    fun `getMetaobjectResult should report not found`() {
        val result = MetaobjectToolResults.getMetaobjectResult("velotrip", GetMetaobjectResult.notFound("gid://shopify/Metaobject/999"))

        texts(result) shouldBe listOf("Boutique : velotrip", "Metaobject introuvable : gid://shopify/Metaobject/999")
        result.isError() shouldBe true
    }

    @Test
    fun `getMetaobjectResult should render the type, reference status and fields when found`() {
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Article", "Guide", "gid://shopify/Article/1", "custom", "guide_theme")),
            truncated = false,
        )
        val result = MetaobjectToolResults.getMetaobjectResult(
            "velotrip",
            GetMetaobjectResult.found("gid://shopify/Metaobject/1", "guide_theme", listOf(MetaobjectFieldValue("title", "Leurres")), referenced),
        )

        texts(result)[1] shouldBe "gid://shopify/Metaobject/1 — type: guide_theme — référencé par : Article \"Guide\" (gid://shopify/Article/1) via custom.guide_theme\n" +
            "  - title: Leurres"
    }

    @Test
    fun `getMetaobjectResult should render the unavailable reference status wording when null`() {
        val result = MetaobjectToolResults.getMetaobjectResult(
            "velotrip",
            GetMetaobjectResult.found("gid://shopify/Metaobject/1", "faq_item", emptyList(), null),
        )

        texts(result)[1] shouldBe "gid://shopify/Metaobject/1 — type: faq_item — statut de référence indisponible (metaobject introuvable lors de la relecture) — " +
            "traité par prudence comme potentiellement référencé\n  (aucun champ)"
    }

    @Test
    fun `getMetaobjectResult should append the truncation caveat when the reference status itself is truncated`() {
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Metaobject", "faq_item", null, "custom", "k")),
            truncated = true,
        )
        val result = MetaobjectToolResults.getMetaobjectResult(
            "velotrip",
            GetMetaobjectResult.found("gid://shopify/Metaobject/1", "faq_item", emptyList(), referenced),
        )

        texts(result)[1] shouldBe "gid://shopify/Metaobject/1 — type: faq_item — référencé par : Metaobject \"faq_item\" via custom.k " +
            "(+ éventuellement d'autres, lecture tronquée)\n  (aucun champ)"
    }

    @Test
    fun `getMetaobjectResult should render the uncertain reference status wording`() {
        val result = MetaobjectToolResults.getMetaobjectResult(
            "velotrip",
            GetMetaobjectResult.found("gid://shopify/Metaobject/1", "faq_item", emptyList(), MetaobjectReferenceStatus.Uncertain),
        )

        texts(result)[1] shouldBe "gid://shopify/Metaobject/1 — type: faq_item — détection de référence incertaine (troncature de la lecture) — " +
            "traité par prudence comme potentiellement référencé, pas déclaré orphelin\n  (aucun champ)"
    }

    @Test
    fun `createMetaobjectResult should report failure`() {
        val result = MetaobjectToolResults.createMetaobjectResult("velotrip", CreateMetaobjectResult.failed("unknown_type", "Type does not exist"))

        texts(result) shouldBe listOf("Boutique : velotrip", "Échec de la création du metaobject \"unknown_type\" : Type does not exist")
        result.isError() shouldBe true
    }

    @Test
    fun `createMetaobjectResult should report the created metaobject id, type and fields`() {
        val result = MetaobjectToolResults.createMetaobjectResult(
            "velotrip",
            CreateMetaobjectResult.created("faq_item", "gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("question", "?"))),
        )

        texts(result)[1] shouldBe "Metaobject gid://shopify/Metaobject/1 (type: faq_item) créé.\n  - question: ?"
    }

    @Test
    fun `updateMetaobjectResult should report not found`() {
        val result = MetaobjectToolResults.updateMetaobjectResult("velotrip", UpdateMetaobjectResult.notFound("gid://shopify/Metaobject/404"))

        texts(result) shouldBe listOf("Boutique : velotrip", "Metaobject introuvable : gid://shopify/Metaobject/404")
        result.isError() shouldBe true
    }

    @Test
    fun `updateMetaobjectResult should report the updated fields with the before type`() {
        val result = MetaobjectToolResults.updateMetaobjectResult(
            "velotrip",
            UpdateMetaobjectResult.updated("gid://shopify/Metaobject/1", "faq_item", listOf(MetaobjectFieldValue("question", "updated?"))),
        )

        texts(result)[1] shouldBe "Metaobject gid://shopify/Metaobject/1 (type: faq_item) mis à jour.\n  - question: updated?"
    }

    @Test
    fun `updateMetaobjectResult should report failure`() {
        val result = MetaobjectToolResults.updateMetaobjectResult("velotrip", UpdateMetaobjectResult.failed("gid://shopify/Metaobject/1", "invalid"))

        texts(result)[1] shouldBe "Échec de la mise à jour du metaobject gid://shopify/Metaobject/1 : invalid"
        result.isError() shouldBe true
    }

    @Test
    fun `deleteMetaobjectResult should report not found`() {
        val result = MetaobjectToolResults.deleteMetaobjectResult("velotrip", DeleteMetaobjectResult.notFound("gid://shopify/Metaobject/404"))

        texts(result) shouldBe listOf("Boutique : velotrip", "Metaobject introuvable : gid://shopify/Metaobject/404")
        result.isError() shouldBe true
    }

    @Test
    fun `deleteMetaobjectResult should report failure`() {
        val result = MetaobjectToolResults.deleteMetaobjectResult("velotrip", DeleteMetaobjectResult.failed("gid://shopify/Metaobject/1", "boom"))

        texts(result)[1] shouldBe "Échec de la suppression du metaobject gid://shopify/Metaobject/1 : boom"
        result.isError() shouldBe true
    }

    @Test
    fun `deleteMetaobjectResult should report the deletion without a pending-reference warning when orphan`() {
        val result = MetaobjectToolResults.deleteMetaobjectResult(
            "velotrip",
            DeleteMetaobjectResult.deleted(
                "gid://shopify/Metaobject/1",
                "faq_item",
                listOf(MetaobjectFieldValue("question", "?")),
                MetaobjectReferenceStatus.Orphan,
            ),
        )

        texts(result)[1] shouldBe "Metaobject gid://shopify/Metaobject/1 (type: faq_item) supprimé définitivement. Champs qu'il contenait :\n" +
            "  - question: ?"
    }

    @Test
    fun `deleteMetaobjectResult should append the pending-reference warning when the deletion was forced despite a reference`() {
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Product", "Sac", "gid://shopify/Product/1", "custom", "faq")),
            truncated = false,
        )
        val result = MetaobjectToolResults.deleteMetaobjectResult(
            "velotrip",
            DeleteMetaobjectResult.deleted(
                "gid://shopify/Metaobject/1",
                "faq_item",
                listOf(MetaobjectFieldValue("question", "?")),
                referenced,
            ),
        )

        texts(result)[1] shouldBe "Metaobject gid://shopify/Metaobject/1 (type: faq_item) supprimé définitivement. Champs qu'il contenait :\n" +
            "  - question: ?\n" +
            "⚠️ Ce metaobject était encore référencé (ou sa détection restait incertaine) au moment de la suppression " +
            "(référencé par : Product \"Sac\" (gid://shopify/Product/1) via custom.faq) — la ou les ressources qui le référençaient ne sont PAS " +
            "nettoyées automatiquement : une référence pendante peut y subsister, à corriger séparément si besoin " +
            "(update_collection_content pour une collection, enrich_product pour un produit)."
    }

    @Test
    fun `deleteMetaobjectResult should refuse with an actionable message naming confirm_referenced_deletion`() {
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Product", "Sac", "gid://shopify/Product/1", "custom", "faq")),
            truncated = false,
        )
        val result = MetaobjectToolResults.deleteMetaobjectResult(
            "velotrip",
            DeleteMetaobjectResult.refused("gid://shopify/Metaobject/1", "faq_item", referenced),
        )

        texts(result)[1] shouldBe "delete_metaobject refusé pour gid://shopify/Metaobject/1 (type: faq_item) : " +
            "référencé par : Product \"Sac\" (gid://shopify/Product/1) via custom.faq. " +
            "Appeler à nouveau avec confirm_referenced_deletion: true pour forcer la suppression malgré cette " +
            "référence (ou cette incertitude de détection)."
        result.isError() shouldBe true
    }

    @Test
    fun `errorResult and invalidGidType should render the store banner`() {
        val invalidGid = MetaobjectToolResults.invalidGidType("velotrip", "metaobject_id", "not-a-gid", "Metaobject")

        texts(invalidGid) shouldBe listOf(
            "Boutique : velotrip",
            "metaobject_id invalide : \"not-a-gid\" n'est pas un identifiant Metaobject (attendu gid://shopify/Metaobject/...).",
        )
        invalidGid.isError() shouldBe true
    }
}
