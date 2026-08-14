package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DeleteMetaobjectUseCaseTest {

    private val json = Json

    @Test
    fun `execute should return NOT_FOUND before any reference check when the metaobject does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val useCase = DeleteMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/404", confirmReferencedDeletion = false).shouldBeRight()

        result.outcome shouldBe DeleteMetaobjectOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `execute should force the deletion and carry a pending-reference warning when confirmReferencedDeletion is true on a referenced metaobject`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"faq_item","fields":[{"key":"question","value":"?"}]}}""",
                ).right(),
            )
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"faq","namespace":"custom",
                        "referencer":{"__typename":"Product","id":"gid://shopify/Product/1","title":"Sac"}}}],
                        "pageInfo":{"hasNextPage":false}}}}""".replace("\n", "").replace(Regex("\\s+"), " "),
                ).right(),
            )
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDelete":{"deletedId":"gid://shopify/Metaobject/1","userErrors":[]}}""",
                ).right(),
            )
        }
        val useCase = DeleteMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = true).shouldBeRight()

        result.outcome shouldBe DeleteMetaobjectOutcome.DELETED
        requireNotNull(result.text) shouldContain "encore référencé"
        gateway.calls shouldHaveSize 3
    }

    @Test
    fun `execute should refuse and never call metaobjectDelete when the metaobject is referenced and confirmReferencedDeletion is omitted`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"faq_item","fields":[]}}""",
                ).right(),
            )
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"faq","namespace":"custom",
                        "referencer":{"__typename":"Product","id":"gid://shopify/Product/1","title":"Sac"}}}],
                        "pageInfo":{"hasNextPage":false}}}}""".replace("\n", "").replace(Regex("\\s+"), " "),
                ).right(),
            )
        }
        val useCase = DeleteMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = false).shouldBeRight()

        result.outcome shouldBe DeleteMetaobjectOutcome.REFUSED
        gateway.calls shouldHaveSize 2
    }
}
