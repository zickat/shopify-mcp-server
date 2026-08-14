package com.zickat.shopifymcpserver.metaobjects.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDeleteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MetaobjectsShopifyRepositoryTest {

    private val json = Json

    @Test
    fun `listDefinitions should report no definition found when the store has none`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":false},"edges":[]}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val listing = repository.listDefinitions("store-1").shouldBeRight()

        listing.definitions shouldBe emptyList()
        listing.truncated shouldBe false
    }

    @Test
    fun `listDefinitions should map every edge and flag a truncated page`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":true},"edges":[
                        {"node":{"type":"faq_item","name":"FAQ Item","metaobjectsCount":3}}
                    ]}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val listing = repository.listDefinitions("store-1").shouldBeRight()

        listing.definitions.single().type shouldBe "faq_item"
        listing.definitions.single().instanceCount shouldBe 3
        listing.truncated shouldBe true
    }

    @Test
    fun `listInstances should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            if (call.variables.jsonObject["cursor"] == JsonNull) {
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-1"},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/1","fields":[]}}
                    ]}}""",
                ).right()
            } else {
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/2","fields":[]}}
                    ]}}""",
                ).right()
            }
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val listing = repository.listInstances("store-1", "faq_item").shouldBeRight()

        listing.instances.map { it.id } shouldBe listOf("gid://shopify/Metaobject/1", "gid://shopify/Metaobject/2")
        listing.truncated shouldBe false
    }

    @Test
    fun `listInstances should stop at MAX_SEARCH_PAGES and report the scan as truncated`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = {
            pageCount += 1
            json.parseToJsonElement(
                """{"metaobjects":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-$pageCount"},"edges":[
                    {"node":{"id":"gid://shopify/Metaobject/$pageCount","fields":[]}}
                ]}}""",
            ).right()
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val listing = repository.listInstances("store-1", "faq_item").shouldBeRight()

        listing.instances shouldHaveSize 10
        listing.truncated shouldBe true
    }

    @Test
    fun `listInstances should render a null field value as null, not a placeholder`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/1","fields":[{"key":"articles","value":null}]}}
                    ]}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val listing = repository.listInstances("store-1", "faq_item").shouldBeRight()

        listing.instances.single().fields.single().value shouldBe null
    }

    @Test
    fun `referenceStatus should return Orphan when referencedBy has no edge and no truncation`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.referenceStatus("store-1", "gid://shopify/Metaobject/1").shouldBeRight() shouldBe MetaobjectReferenceStatus.Orphan
    }

    @Test
    fun `referenceStatus should return Uncertain when referencedBy has no edge but the read was truncated`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":true}}}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.referenceStatus("store-1", "gid://shopify/Metaobject/1").shouldBeRight() shouldBe MetaobjectReferenceStatus.Uncertain
    }

    @Test
    fun `referenceStatus should return Referenced with the resolved referencer when an edge is present`() {
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
        val repository = MetaobjectsShopifyRepository(gateway)

        val status = repository.referenceStatus("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        status shouldBe MetaobjectReferenceStatus.Referenced(
            references = listOf(MetaobjectReferencer("Article", "Guide", "gid://shopify/Article/1", "custom", "guide_theme")),
            truncated = false,
        )
    }

    @Test
    fun `referenceStatus should fall back to the referencer type when no title nor type is resolved`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[
                        {"node":{"key":"k","namespace":"custom","referencer":{"__typename":"Order"}}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val status = repository.referenceStatus("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        (status as MetaobjectReferenceStatus.Referenced).references.single().referencerTitle shouldBe "(ressource non résolue)"
    }

    @Test
    fun `referenceStatus should return null when the metaobject cannot be found on recheck`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.referenceStatus("store-1", "gid://shopify/Metaobject/999").shouldBeRight() shouldBe null
    }

    @Test
    fun `get should return null when the metaobject does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.get("store-1", "gid://shopify/Metaobject/999").shouldBeRight() shouldBe null
    }

    @Test
    fun `get should return the snapshot when the metaobject exists`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"guide_theme","fields":[{"key":"title","value":"Leurres"}]}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val snapshot = repository.get("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        snapshot shouldBe MetaobjectSnapshot("gid://shopify/Metaobject/1", "guide_theme", listOf(MetaobjectFieldValue("title", "Leurres")))
    }

    @Test
    fun `getBeforeUpdate should emit the GetMetaobjectBeforeUpdate operation, distinct from get`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.getBeforeUpdate("store-1", "gid://shopify/Metaobject/1")

        gateway.calls.single().query shouldBe MetaobjectsGraphQL.GET_METAOBJECT_BEFORE_UPDATE_QUERY
    }

    @Test
    fun `getBeforeDelete should emit the GetMetaobjectBeforeDelete operation, distinct from get`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.getBeforeDelete("store-1", "gid://shopify/Metaobject/1")

        gateway.calls.single().query shouldBe MetaobjectsGraphQL.GET_METAOBJECT_BEFORE_DELETE_QUERY
    }

    @Test
    fun `create should resolve field types then convert rich_text_field values before mutating`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDefinitionByType":{"fieldDefinitions":[{"key":"body","type":{"name":"rich_text_field"}}]}}""",
                ).right(),
            )
            enqueue(json.parseToJsonElement("""{"metaobjectCreate":{"metaobject":{"id":"gid://shopify/Metaobject/1"},"userErrors":[]}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "faq_item", listOf(MetaobjectFieldInput("body", "Plain text."))).shouldBeRight()

        outcome shouldBe MetaobjectWriteOutcome.Success("gid://shopify/Metaobject/1")
        gateway.calls shouldHaveSize 2
        val fieldValue = (gateway.calls[1].variables.jsonObject["metaobject"]!!.jsonObject["fields"] as JsonArray)
            .single().jsonObject["value"]?.jsonPrimitive?.content
        json.parseToJsonElement(requireNotNull(fieldValue)).jsonObject["type"]?.jsonPrimitive?.content shouldBe "root"
    }

    @Test
    fun `create should report a failed outcome when Shopify returns userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectCreate":{"metaobject":null,"userErrors":[{"field":["type"],"message":"Type does not exist"}]}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "unknown_type", listOf(MetaobjectFieldInput("title", "x"))).shouldBeRight()

        outcome shouldBe MetaobjectWriteOutcome.Failed("type : Type does not exist")
    }

    @Test
    fun `update should resolve field types using the given type and report success`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
            enqueue(json.parseToJsonElement("""{"metaobjectUpdate":{"metaobject":{"id":"gid://shopify/Metaobject/1"},"userErrors":[]}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        val outcome = repository.update("store-1", "gid://shopify/Metaobject/1", "faq_item", listOf(MetaobjectFieldInput("question", "updated?"))).shouldBeRight()

        outcome shouldBe MetaobjectWriteOutcome.Success("gid://shopify/Metaobject/1")
        gateway.calls[0].variables.jsonObject["type"]?.jsonPrimitive?.content shouldBe "faq_item"
    }

    @Test
    fun `delete should report Deleted when Shopify returns a deletedId with no userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDelete":{"deletedId":"gid://shopify/Metaobject/1","userErrors":[]}}""").right())
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.delete("store-1", "gid://shopify/Metaobject/1").shouldBeRight() shouldBe MetaobjectDeleteOutcome.Deleted
    }

    @Test
    fun `delete should report a failed outcome when Shopify returns userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDelete":{"deletedId":null,"userErrors":[{"field":[],"message":"boom"}]}}""",
                ).right(),
            )
        }
        val repository = MetaobjectsShopifyRepository(gateway)

        repository.delete("store-1", "gid://shopify/Metaobject/1").shouldBeRight() shouldBe MetaobjectDeleteOutcome.Failed("boom")
    }
}
