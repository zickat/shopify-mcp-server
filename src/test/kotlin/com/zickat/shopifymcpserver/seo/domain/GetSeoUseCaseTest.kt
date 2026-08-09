package com.zickat.shopifymcpserver.seo.domain

import arrow.core.right
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class GetSeoUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = GetSeoUseCase(gateway)

    private fun JsonElement.idVariable(): String = (this as JsonObject).getValue("id").jsonPrimitive.content

    @Test
    fun `should return Found with the native seo fields when the resource type dispatches to the native mechanism`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":{"__typename":"Product","title":"Item","seo":{"title":"T","description":"D"}}}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1").shouldBeRight()

        result shouldBe GetSeoResult.found("Item", "T", "D")
    }

    @Test
    fun `should return Found with the metafield seo fields when the resource type dispatches to the metafield mechanism`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"node":{"__typename":"Page","title":"Item","titleTag":{"value":"T"},"descriptionTag":{"value":"D"}}}""",
                ).right(),
            )
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.PAGE, "gid://shopify/Page/1").shouldBeRight()

        result shouldBe GetSeoResult.found("Item", "T", "D")
    }

    @Test
    fun `should return NotFound when the node does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":null}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999").shouldBeRight()

        result shouldBe GetSeoResult.NotFound
    }

    @Test
    fun `should return NotFound and emit no further call when resource_id resolves to a different type than resource_type — the mismatch guard`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"node":{"__typename":"Product","title":"Wrong type","seo":{"title":null,"description":null}}}""",
                ).right(),
            )
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Product/1").shouldBeRight()

        result shouldBe GetSeoResult.NotFound
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `should return NotFound when the response carries no node key at all`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{}""").right())
        }

        val result = useCase(gateway).execute("store-1", SeoResourceType.ARTICLE, "gid://shopify/Article/1").shouldBeRight()

        result shouldBe GetSeoResult.NotFound
    }

    @Test
    fun `should emit exactly one graphql call carrying the resource_id as the id variable`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"node":null}""").right())
        }

        useCase(gateway).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/42").shouldBeRight()

        gateway.calls shouldHaveSize 1
        val call = gateway.calls.single()
        call.storeId shouldBe "store-1"
        call.variables.idVariable() shouldBe "gid://shopify/Collection/42"
    }
}
