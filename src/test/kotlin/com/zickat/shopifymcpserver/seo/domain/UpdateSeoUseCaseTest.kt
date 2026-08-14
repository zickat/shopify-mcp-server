package com.zickat.shopifymcpserver.seo.domain

import arrow.core.right
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class UpdateSeoUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = UpdateSeoUseCase(gateway)

    @Test
    fun `should return a no-op outcome and emit no network call at all when both seo fields are omitted`() {
        val gateway = ShopifyAdminGatewayFake()

        val result = useCase(gateway).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", null, null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NO_OP
        gateway.calls shouldHaveSize 0
    }

    @Test
    fun `should return NotFound and emit no write call when the node does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":null}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `should return NotFound and emit no write call when resource_id resolves to a different type than resource_type`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Wrong type","seo":{"title":null,"description":null}}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Product/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `should send an explicit null description in the fused native mutation when the description was never set and is not provided`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":null,"description":null}}}""").right())
            enqueue(json.parseToJsonElement("""{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.UPDATED
        result.finalMetaDescription shouldBe null
        val writeVariables = gateway.calls[1].variables.jsonObject.getValue("input").jsonObject
        writeVariables.getValue("seo").jsonObject.getValue("title") shouldBe JsonPrimitive("New title")
        writeVariables.getValue("seo").jsonObject.getValue("description") shouldBe JsonNull
    }

    @Test
    fun `should reach for collectionUpdate, not productUpdate, when the native mechanism resource_type is collection`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Collection","title":"Item","seo":{"title":"Old","description":"Old desc"}}}""").right())
            enqueue(json.parseToJsonElement("""{"collectionUpdate":{"collection":{"id":"gid://shopify/Collection/1"},"userErrors":[]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.UPDATED
        result.finalMetaDescription shouldBe "Old desc"
        gateway.calls[1].query shouldBe (
            "mutation UpdateCollectionSeo(\$input: CollectionInput!) {\n" +
                "        collectionUpdate(input: \$input) { collection { id } userErrors { field message } }\n" +
                "      }"
            )
    }

    @Test
    fun `should send only the provided metafield key on the metafield mechanism, leaving the other untouched`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Page","title":"Item","titleTag":{"value":"Old title"},"descriptionTag":{"value":"Old desc"}}}""").right())
            enqueue(json.parseToJsonElement("""{"metafieldsSet":{"metafields":[{"id":"gid://shopify/Metafield/1","key":"description_tag"}],"userErrors":[]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.PAGE, "gid://shopify/Page/1", null, "New description").shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.UPDATED
        result.titleModified shouldBe false
        result.descriptionModified shouldBe true
        result.finalMetaTitle shouldBe "Old title"
        val metafields = gateway.calls[1].variables.jsonObject.getValue("metafields").jsonArray
        metafields shouldHaveSize 1
        metafields.single().jsonObject.getValue("key").jsonPrimitive.content shouldBe "description_tag"
    }

    @Test
    fun `should return a failed outcome carrying the formatted Shopify user error when the write mutation rejects it`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":null,"description":null}}}""").right())
            enqueue(json.parseToJsonElement("""{"productUpdate":{"product":null,"userErrors":[{"field":["seo","title"],"message":"is too long"}]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.FAILED
        result.failureDetail shouldBe "seo.title : is too long"
    }
}
