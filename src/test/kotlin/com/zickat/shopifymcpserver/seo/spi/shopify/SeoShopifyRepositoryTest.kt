package com.zickat.shopifymcpserver.seo.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoWriteOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SeoShopifyRepositoryTest {

    private val json = Json

    private fun JsonElement.idVariable(): String = (this as kotlinx.serialization.json.JsonObject).getValue("id").jsonPrimitive.content

    @Nested
    inner class GetTest {

        @Test
        fun `get should return the native seo fields when the resource type dispatches to the native mechanism`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":"T","description":"D"}}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val snapshot = repository.get("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1").shouldBeRight()

            snapshot shouldBe SeoSnapshot("Item", "T", "D")
        }

        @Test
        fun `get should return the metafield seo fields when the resource type dispatches to the metafield mechanism`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"node":{"__typename":"Page","title":"Item","titleTag":{"value":"T"},"descriptionTag":{"value":"D"}}}""",
                    ).right(),
                )
            }
            val repository = SeoShopifyRepository(gateway)

            val snapshot = repository.get("store-1", SeoResourceType.PAGE, "gid://shopify/Page/1").shouldBeRight()

            snapshot shouldBe SeoSnapshot("Item", "T", "D")
        }

        @Test
        fun `get should return null when the node does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":null}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            repository.get("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999").shouldBeRight() shouldBe null
        }

        @Test
        fun `get should return null and emit no further call when resource_id resolves to a different type than resource_type — the mismatch guard`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"node":{"__typename":"Product","title":"Wrong type","seo":{"title":null,"description":null}}}""",
                    ).right(),
                )
            }
            val repository = SeoShopifyRepository(gateway)

            val snapshot = repository.get("store-1", SeoResourceType.COLLECTION, "gid://shopify/Product/1").shouldBeRight()

            snapshot shouldBe null
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `get should return null when the response carries no node key at all`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            repository.get("store-1", SeoResourceType.ARTICLE, "gid://shopify/Article/1").shouldBeRight() shouldBe null
        }

        @Test
        fun `get should emit exactly one graphql call carrying the resource_id as the id variable`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":null}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            repository.get("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/42").shouldBeRight()

            gateway.calls shouldHaveSize 1
            val call = gateway.calls.single()
            call.storeId shouldBe "store-1"
            call.variables.idVariable() shouldBe "gid://shopify/Collection/42"
        }
    }

    @Nested
    inner class UpdateTest {

        @Test
        fun `update should return NotFound and emit no write call when the node does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":null}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999", "New title", null).shouldBeRight()

            outcome shouldBe SeoWriteOutcome.NotFound
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `update should return NotFound and emit no write call when resource_id resolves to a different type than resource_type`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Wrong type","seo":{"title":null,"description":null}}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.COLLECTION, "gid://shopify/Product/1", "New title", null).shouldBeRight()

            outcome shouldBe SeoWriteOutcome.NotFound
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `update should send an explicit null description in the fused native mutation when the description was never set and is not provided`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":null,"description":null}}}""").right())
                enqueue(json.parseToJsonElement("""{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

            (outcome as SeoWriteOutcome.Updated).finalMetaDescription shouldBe null
            val writeVariables = gateway.calls[1].variables.jsonObject.getValue("input").jsonObject
            writeVariables.getValue("seo").jsonObject.getValue("title") shouldBe JsonPrimitive("New title")
            writeVariables.getValue("seo").jsonObject.getValue("description") shouldBe JsonNull
        }

        @Test
        fun `update should reach for collectionUpdate, not productUpdate, when the native mechanism resource_type is collection`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Collection","title":"Item","seo":{"title":"Old","description":"Old desc"}}}""").right())
                enqueue(json.parseToJsonElement("""{"collectionUpdate":{"collection":{"id":"gid://shopify/Collection/1"},"userErrors":[]}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/1", "New title", null).shouldBeRight()

            (outcome as SeoWriteOutcome.Updated).finalMetaDescription shouldBe "Old desc"
            gateway.calls[1].query shouldBe (
                "mutation UpdateCollectionSeo(\$input: CollectionInput!) {\n" +
                    "        collectionUpdate(input: \$input) { collection { id } userErrors { field message } }\n" +
                    "      }"
                )
        }

        @Test
        fun `update should send only the provided metafield key on the metafield mechanism, leaving the other untouched`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Page","title":"Item","titleTag":{"value":"Old title"},"descriptionTag":{"value":"Old desc"}}}""").right())
                enqueue(json.parseToJsonElement("""{"metafieldsSet":{"metafields":[{"id":"gid://shopify/Metafield/1","key":"description_tag"}],"userErrors":[]}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.PAGE, "gid://shopify/Page/1", null, "New description").shouldBeRight()

            val updated = outcome as SeoWriteOutcome.Updated
            updated.finalMetaTitle shouldBe "Old title"
            val metafields = gateway.calls[1].variables.jsonObject.getValue("metafields").jsonArray
            metafields shouldHaveSize 1
            metafields.single().jsonObject.getValue("key").jsonPrimitive.content shouldBe "description_tag"
        }

        @Test
        fun `update should return a failed outcome carrying the formatted Shopify user error when the write mutation rejects it`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":null,"description":null}}}""").right())
                enqueue(json.parseToJsonElement("""{"productUpdate":{"product":null,"userErrors":[{"field":["seo","title"],"message":"is too long"}]}}""").right())
            }
            val repository = SeoShopifyRepository(gateway)

            val outcome = repository.update("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

            (outcome as SeoWriteOutcome.Failed).detail shouldBe "seo.title : is too long"
        }
    }
}
