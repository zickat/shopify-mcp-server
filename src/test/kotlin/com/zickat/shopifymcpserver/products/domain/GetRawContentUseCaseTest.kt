package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GetRawContentUseCaseTest {

    private val json = Json

    @Test
    fun `should return NotFound and emit no second call when the product does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":null}""").right())
        }
        val useCase = GetRawContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe GetRawContentOutcome.NOT_FOUND
        result.productId shouldBe "gid://shopify/Product/999"
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `should combine the product query and the media query into a single rendered report`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("GetRawContent") ->
                    json.parseToJsonElement(
                        """{"product":{
                            "id":"gid://shopify/Product/1","title":"Maillot","handle":"maillot","status":"ACTIVE",
                            "descriptionHtml":"<p>Un <strong>maillot</strong> léger</p>",
                            "priceRangeV2":{"minVariantPrice":{"amount":"29.99","currencyCode":"EUR"},"maxVariantPrice":{"amount":"29.99","currencyCode":"EUR"}},
                            "variants":{"edges":[
                                {"node":{"id":"gid://shopify/ProductVariant/1","selectedOptions":[{"name":"Couleur","value":"Olive"}],"media":{"edges":[{"node":{"id":"gid://shopify/MediaImage/1"}}]}}},
                                {"node":{"id":"gid://shopify/ProductVariant/2","selectedOptions":[{"name":"Couleur","value":"Noir"}],"media":{"edges":[]}}}
                            ]},
                            "options":[{"id":"gid://shopify/ProductOption/1","name":"Couleur","optionValues":[{"id":"o1","name":"Olive"},{"id":"o2","name":"Noir"}]}]
                        }}""",
                    ).right()
                else ->
                    json.parseToJsonElement(
                        """{"product":{"status":"ACTIVE","media":{"edges":[
                            {"node":{"id":"gid://shopify/MediaImage/1","alt":"Alt du produit","image":{"url":"https://cdn/1.jpg","altText":null}}}
                        ]}}}""",
                    ).right()
            }
        }
        val useCase = GetRawContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe GetRawContentOutcome.FOUND
        val text = requireNotNull(result.text)
        text shouldContain "Titre : Maillot"
        text shouldContain "Handle : maillot"
        text shouldContain "Prix : 29.99 EUR"
        text shouldContain "Nombre d'images : 1"
        text shouldContain "alt : \"Alt du produit\""
        text shouldContain "variante(s) : Couleur: Olive"
        text shouldContain "Description source (texte brut, balises HTML retirées) :\nUn maillot léger"
        text shouldContain "Couleur : Olive, Noir"
        text shouldContain "Couleur: Olive — variant_id : gid://shopify/ProductVariant/1"
        text shouldContain "Couleur: Noir — variant_id : gid://shopify/ProductVariant/2"
        gateway.calls.size shouldBe 2
    }

    @Test
    fun `should render a price range when min and max variant prices differ`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("GetRawContent") ->
                    json.parseToJsonElement(
                        """{"product":{
                            "id":"gid://shopify/Product/1","title":"T","handle":"t","status":"ACTIVE",
                            "descriptionHtml":"",
                            "priceRangeV2":{"minVariantPrice":{"amount":"10.00","currencyCode":"EUR"},"maxVariantPrice":{"amount":"20.00","currencyCode":"EUR"}},
                            "variants":{"edges":[]},
                            "options":[]
                        }}""",
                    ).right()
                else -> json.parseToJsonElement("""{"product":{"status":"ACTIVE","media":{"edges":[]}}}""").right()
            }
        }
        val useCase = GetRawContentUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        requireNotNull(result.text) shouldContain "Prix : 10.00–20.00 EUR"
    }
}
