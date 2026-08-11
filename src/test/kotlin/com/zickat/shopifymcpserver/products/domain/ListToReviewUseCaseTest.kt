package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ListToReviewUseCaseTest {

    private val json = Json

    @Test
    fun `should report none to review for an empty result, naming the requested resource type`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = ListToReviewUseCase(gateway)

        val result = useCase.execute("store-1", ToReviewResourceType.PRODUCT).shouldBeRight()

        result.text shouldBe "Aucun(e) product à review actuellement."
    }

    @Test
    fun `should keep only nodes whose content_status is exactly to_review`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Collection/1","title":"To review","handle":"to-review","contentStatus":{"value":"to_review"}}},
                        {"node":{"id":"gid://shopify/Collection/2","title":"Blocked","handle":"blocked","contentStatus":{"value":"blocked"}}},
                        {"node":{"id":"gid://shopify/Collection/3","title":"Untreated","handle":"untreated","contentStatus":null}}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = ListToReviewUseCase(gateway)

        val result = useCase.execute("store-1", ToReviewResourceType.COLLECTION).shouldBeRight()

        result.text shouldBe "1 collection(s) à review :\n- To review (to-review) — id: gid://shopify/Collection/1"
    }

    @Test
    fun `should follow the cursor across pages of the article scan until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            if (call.variables.toString().contains("null")) {
                json.parseToJsonElement(
                    """{"articles":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-1"},"edges":[
                        {"node":{"id":"gid://shopify/Article/1","title":"A","handle":"a","contentStatus":{"value":"to_review"}}}
                    ]}}""",
                ).right()
            } else {
                json.parseToJsonElement(
                    """{"articles":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Article/2","title":"B","handle":"b","contentStatus":{"value":"to_review"}}}
                    ]}}""",
                ).right()
            }
        }
        val useCase = ListToReviewUseCase(gateway)

        val result = useCase.execute("store-1", ToReviewResourceType.ARTICLE).shouldBeRight()

        result.text shouldContain "2 article(s) à review :"
        result.text shouldContain "gid://shopify/Article/1"
        result.text shouldContain "gid://shopify/Article/2"
        gateway.calls.size shouldBe 2
    }
}
