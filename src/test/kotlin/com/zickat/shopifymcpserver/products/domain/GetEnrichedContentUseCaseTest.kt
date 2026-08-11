package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GetEnrichedContentUseCaseTest {

    private val json = Json

    @Test
    fun `should return NotFound when the product does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":null}""").right())
        }
        val useCase = GetEnrichedContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe GetEnrichedContentOutcome.NOT_FOUND
        result.productId shouldBe "gid://shopify/Product/999"
    }

    @Test
    fun `should report untreated when neither content_status nor summary_points is set, and the not-yet-captured placeholders`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"product":{
                        "id":"gid://shopify/Product/1","title":"T","handle":"t","status":"DRAFT",
                        "productType":"","tags":[],"descriptionHtml":"",
                        "contentStatus":null,"summaryPoints":null,"whyRecommend":null,"howToUse":null,
                        "specs":null,"faq":null,"complementaryProducts":null,
                        "originalTitle":null,"originalDescriptionHtml":null,
                        "relatedGuides":null,"relatedGuidesSource":null,"idealFor":null
                    }}""",
                ).right(),
            )
        }
        val useCase = GetEnrichedContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe GetEnrichedContentOutcome.FOUND
        val text = requireNotNull(result.text)
        text shouldContain "Statut pipeline : untreated"
        text shouldContain "Titre original fournisseur (avant 1ère réécriture) : (non capturé — produit jamais réécrit par enrich_product)"
        text shouldContain "Description originale fournisseur (avant 1ère réécriture) : (non capturée — produit jamais réécrit par enrich_product)"
        text shouldContain "source: auto — projection automatique, plafonnée à 3 par publishedAt décroissant"
        text shouldContain "Idéal pour : (aucun — plomberie de lecture seule, aucun contenu produit tant que la doctrine éditoriale n'est pas tranchée)"
    }

    @Test
    fun `should report published when summary_points exist but content_status is absent — BUG-01`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"product":{
                        "id":"gid://shopify/Product/1","title":"T","handle":"t","status":"ACTIVE",
                        "productType":"","tags":[],"descriptionHtml":"",
                        "contentStatus":null,"summaryPoints":{"value":"[\"a\"]"},"whyRecommend":null,"howToUse":null,
                        "specs":null,"faq":null,"complementaryProducts":null,
                        "originalTitle":null,"originalDescriptionHtml":null,
                        "relatedGuides":null,"relatedGuidesSource":null,"idealFor":null
                    }}""",
                ).right(),
            )
        }
        val useCase = GetEnrichedContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        requireNotNull(result.text) shouldContain "Statut pipeline : published"
    }

    @Test
    fun `should render specs, faq, complementary products and a manual related_guides override`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"product":{
                        "id":"gid://shopify/Product/1","title":"T","handle":"t","status":"ACTIVE",
                        "productType":"Sacoche","tags":["a","b"],"descriptionHtml":"<p>Desc</p>",
                        "contentStatus":{"value":"to_review"},
                        "summaryPoints":{"value":"[\"Point 1\"]"},
                        "whyRecommend":{"value":"{\"type\":\"root\",\"children\":[{\"type\":\"paragraph\",\"children\":[{\"type\":\"text\",\"value\":\"Why\"}]}]}"},
                        "howToUse":{"value":"{\"type\":\"root\",\"children\":[{\"type\":\"paragraph\",\"children\":[{\"type\":\"text\",\"value\":\"How\"}]}]}"},
                        "specs":{"references":{"edges":[{"node":{"label":{"value":"Poids"},"value":{"value":"100g"}}}]}},
                        "faq":{"references":{"edges":[{"node":{"question":{"value":"Q?"},"answer":{"value":"A."}}}]}},
                        "complementaryProducts":{"references":{"edges":[{"node":{"id":"gid://shopify/Product/2","title":"Autre"}}]}},
                        "originalTitle":{"value":"Old title"},
                        "originalDescriptionHtml":{"value":"<p>Old &amp; desc</p>"},
                        "relatedGuides":{"references":{"edges":[{"node":{"id":"gid://shopify/Article/1","handle":"guide","title":"Guide"}}]}},
                        "relatedGuidesSource":{"value":"manual"},
                        "idealFor":{"value":"[\"bikepacking\"]"}
                    }}""",
                ).right(),
            )
        }
        val useCase = GetEnrichedContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        val text = requireNotNull(result.text)
        text shouldContain "Specs :\n  - Poids : 100g"
        text shouldContain "FAQ :\n  - Q: Q?\n    R: A."
        text shouldContain "Produits complémentaires :\n  - Autre (gid://shopify/Product/2)"
        text shouldContain "Titre original fournisseur (avant 1ère réécriture) : Old title"
        text shouldContain "Description originale fournisseur (avant 1ère réécriture) : Old & desc"
        text shouldContain "source: manual — override figé, la projection automatique ne le touche pas"
        text shouldContain "Guide (/blogs/guides/guide, gid://shopify/Article/1)"
        text shouldContain "Idéal pour : bikepacking"
        text shouldContain "Pourquoi le recommander :\nWhy"
        text shouldContain "Comment l'utiliser :\nHow"
    }
}
