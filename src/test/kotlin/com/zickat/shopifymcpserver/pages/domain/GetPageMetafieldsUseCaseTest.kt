package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GetPageMetafieldsUseCaseTest {

    private val json = Json

    @Test
    fun `execute without keys should report page not found when the page does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"page":null}""").right())
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/999", null).shouldBeRight()

        result.outcome shouldBe GetPageMetafieldsOutcome.NOT_FOUND
        result.pageId shouldBe "gid://shopify/Page/999"
    }

    @Test
    fun `execute without keys should report no metafield defined when the page has none`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Contact","metafields":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.text shouldBe "Page \"Contact\" — aucun metafield custom.* défini."
    }

    @Test
    fun `execute without keys should list every custom metafield found`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[
                        {"node":{"key":"summary","type":"single_line_text_field","value":"Résumé"}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.text shouldBe "Page \"Guides\" — 1 metafield(s) custom.* :\n- summary: Résumé (single_line_text_field)"
    }

    @Test
    fun `execute with keys should filter to the requested keys and report the absent one as non defini`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[
                        {"node":{"key":"summary","type":"single_line_text_field","value":"Résumé"}},
                        {"node":{"key":"themes","type":"list.metaobject_reference","value":"[]"}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", listOf("themes", "nonexistent_key")).shouldBeRight()

        result.text shouldBe "Page \"Guides\" — 1 metafield(s) custom.* parmi les clés demandées :\n" +
            "- themes: [] (list.metaobject_reference)\n" +
            "Non défini : nonexistent_key"
    }

    @Test
    fun `execute with keys should report every key as absent when none of them is defined`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", listOf("themes")).shouldBeRight()

        result.text shouldBe "Page \"Guides\" — 0 metafield(s) custom.* parmi les clés demandées :\n" +
            "  (aucune des clés demandées n'est définie)\n" +
            "Non défini : themes"
    }

    @Test
    fun `execute with an empty keys list should behave like keys omitted, listing every metafield`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[
                        {"node":{"key":"summary","type":"single_line_text_field","value":"Résumé"}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", emptyList()).shouldBeRight()

        result.text shouldBe "Page \"Guides\" — 1 metafield(s) custom.* :\n- summary: Résumé (single_line_text_field)"
    }

    @Test
    fun `execute should flag a truncated metafields page explicitly`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[
                        {"node":{"key":"summary","type":"single_line_text_field","value":"Résumé"}}
                    ],"pageInfo":{"hasNextPage":true}}}}""",
                ).right(),
            )
        }
        val useCase = GetPageMetafieldsUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.text shouldBe "Page \"Guides\" — 1 metafield(s) custom.* :\n- summary: Résumé (single_line_text_field)" +
            "\n\n⚠️ Lecture des metafields tronquée — d'autres metafields custom.* peuvent exister au-delà de la borne."
    }
}
