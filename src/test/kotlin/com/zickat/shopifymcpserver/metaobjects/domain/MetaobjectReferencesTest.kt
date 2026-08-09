package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class MetaobjectReferencesTest {

    private val json = Json

    @Test
    fun `fetchMetaobjectReferences should return Orphan when referencedBy has no edge and no truncation`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        status shouldBe MetaobjectReferenceStatus.Orphan
    }

    @Test
    fun `fetchMetaobjectReferences should return Uncertain when referencedBy has no edge but the read was truncated`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":true}}}}""",
                ).right(),
            )
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        status shouldBe MetaobjectReferenceStatus.Uncertain
    }

    @Test
    fun `fetchMetaobjectReferences should return Referenced with the resolved referencer when an edge is present`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[
                        {"node":{"key":"guide_theme","namespace":"custom","referencer":
                            {"__typename":"Article","id":"gid://shopify/Article/1","title":"Guide"}}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        status shouldBe MetaobjectReferenceStatus.Referenced(
            references = listOf(
                MetaobjectReferencer(
                    referencerType = "Article",
                    referencerTitle = "Guide",
                    referencerId = "gid://shopify/Article/1",
                    namespace = "custom",
                    key = "guide_theme",
                ),
            ),
            truncated = false,
        )
    }

    @Test
    fun `fetchMetaobjectReferences should mark Referenced as truncated when more edges may exist beyond the ones read`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[
                        {"node":{"key":"k","namespace":"custom","referencer":{"__typename":"Metaobject","type":"faq_item"}}}
                    ],"pageInfo":{"hasNextPage":true}}}}""",
                ).right(),
            )
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        (status as MetaobjectReferenceStatus.Referenced).truncated shouldBe true
    }

    @Test
    fun `fetchMetaobjectReferences should return null when the metaobject cannot be found on recheck`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/999").shouldBeRight()

        status shouldBe null
    }

    @Test
    fun `fetchMetaobjectReferences should fall back to the referencer type when no title nor type is resolved`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[
                        {"node":{"key":"k","namespace":"custom","referencer":{"__typename":"Order"}}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }

        val status = fetchMetaobjectReferences(gateway, "store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        (status as MetaobjectReferenceStatus.Referenced).references.single().referencerTitle shouldBe "(ressource non résolue)"
    }

    @Test
    fun `isOrphan should be true only for the Orphan branch`() {
        isOrphan(MetaobjectReferenceStatus.Orphan) shouldBe true
        isOrphan(MetaobjectReferenceStatus.Uncertain) shouldBe false
        isOrphan(MetaobjectReferenceStatus.Referenced(emptyList(), truncated = false)) shouldBe false
    }

    @Test
    fun `formatReferenceStatus should render the four distinct branches with their own wording`() {
        formatReferenceStatus(null) shouldBe
            "statut de référence indisponible (metaobject introuvable lors de la relecture) — traité par " +
            "prudence comme potentiellement référencé"
        formatReferenceStatus(MetaobjectReferenceStatus.Orphan) shouldBe "orphelin (aucune référence entrante trouvée)"
        formatReferenceStatus(MetaobjectReferenceStatus.Uncertain) shouldBe
            "détection de référence incertaine (troncature de la lecture) — traité par prudence comme " +
            "potentiellement référencé, pas déclaré orphelin"
        formatReferenceStatus(
            MetaobjectReferenceStatus.Referenced(
                references = listOf(
                    MetaobjectReferencer("Article", "Guide", "gid://shopify/Article/1", "custom", "guide_theme"),
                ),
                truncated = false,
            ),
        ) shouldBe "référencé par : Article \"Guide\" (gid://shopify/Article/1) via custom.guide_theme"
    }

    @Test
    fun `formatReferenceStatus should append the truncation caveat when a Referenced status is itself truncated`() {
        val status = MetaobjectReferenceStatus.Referenced(
            references = listOf(MetaobjectReferencer("Metaobject", "faq_item", null, "custom", "k")),
            truncated = true,
        )

        formatReferenceStatus(status) shouldBe
            "référencé par : Metaobject \"faq_item\" via custom.k (+ éventuellement d'autres, lecture tronquée)"
    }

    @Test
    fun `formatFields should render a placeholder for an empty field list`() {
        formatFields(emptyList()) shouldBe "  (aucun champ)"
    }
}
