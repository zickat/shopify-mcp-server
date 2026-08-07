package com.zickat.shopifymcpserver.catalog_status.domain

import arrow.core.right
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchStatusFilter
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class SearchResourcesUseCaseTest {

    private fun metafield(value: String?): JsonElement = if (value == null) {
        JsonNull
    } else {
        buildJsonObject { put("value", value) }
    }

    private fun edge(
        id: String,
        title: String = "Title",
        handle: String = "handle",
        contentStatus: String? = null,
        summary: String? = null,
        secondarySignal: String? = null,
    ): JsonElement = buildJsonObject {
        put(
            "node",
            buildJsonObject {
                put("id", id)
                put("title", title)
                put("handle", handle)
                put("contentStatus", metafield(contentStatus))
                put("summary", metafield(summary))
                put("secondarySignal", metafield(secondarySignal))
            },
        )
    }

    private fun page(pluralField: String, edges: List<JsonElement>, hasNextPage: Boolean = false, endCursor: String? = null): JsonElement =
        buildJsonObject {
            put(
                pluralField,
                buildJsonObject {
                    put(
                        "pageInfo",
                        buildJsonObject {
                            put("hasNextPage", hasNextPage)
                            put("endCursor", endCursor?.let { JsonPrimitive(it) } ?: JsonNull)
                        },
                    )
                    put("edges", buildJsonArray { edges.forEach { add(it) } })
                },
            )
        }

    @Test
    fun `execute should not treat a collection as untreated when content_status is absent but summary is non-empty`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("collections", listOf(edge(id = "gid://1", summary = "Already enriched"))).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources shouldBe emptyList()
    }

    @Test
    fun `execute should not treat a collection as untreated when content_status is absent but intro_text is non-empty rich text`() {
        val gateway = ShopifyAdminGatewayFake()
        val introText = """{"type":"root","children":[{"type":"paragraph","children":[{"type":"text","value":"Some intro"}]}]}"""
        gateway.default = page("collections", listOf(edge(id = "gid://1", secondarySignal = introText))).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources shouldBe emptyList()
    }

    @Test
    fun `execute should treat a collection as untreated when intro_text is a JSON-non-null but textually empty Lexical document`() {
        val gateway = ShopifyAdminGatewayFake()
        val emptyLexical = """{"type":"root","children":[]}"""
        gateway.default = page("collections", listOf(edge(id = "gid://1", secondarySignal = emptyLexical))).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.map { it.id } shouldBe listOf("gid://1")
    }

    @Test
    fun `execute should not treat an article as untreated when content_status is absent but sections is a non-empty list`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("articles", listOf(edge(id = "gid://1", secondarySignal = """["one section"]"""))).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.ARTICLE, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources shouldBe emptyList()
    }

    @Test
    fun `execute should treat an article as untreated when sections is a JSON-non-null but empty array`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("articles", listOf(edge(id = "gid://1", secondarySignal = "[]"))).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.ARTICLE, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.map { it.id } shouldBe listOf("gid://1")
    }

    @Test
    fun `execute should filter to_review and blocked strictly on content_status`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(
            "collections",
            listOf(
                edge(id = "gid://to-review", contentStatus = "to_review"),
                edge(id = "gid://blocked", contentStatus = "blocked"),
                edge(id = "gid://untreated"),
            ),
        ).right()
        val useCase = SearchResourcesUseCase(gateway)

        val toReview = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.TO_REVIEW).shouldBeRight()
        val blocked = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.BLOCKED).shouldBeRight()

        toReview.resources.map { it.id } shouldBe listOf("gid://to-review")
        blocked.resources.map { it.id } shouldBe listOf("gid://blocked")
    }

    @Test
    fun `execute with all should return every resource regardless of status and label untreated ones accordingly`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(
            "collections",
            listOf(
                edge(id = "gid://to-review", contentStatus = "to_review"),
                edge(id = "gid://untreated"),
            ),
        ).right()
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.ALL).shouldBeRight()

        result.resources.map { it.id to it.contentStatus } shouldBe listOf(
            "gid://to-review" to "to_review",
            "gid://untreated" to "untreated",
        )
    }

    @Test
    fun `execute should trim a blank query down to null in the sent variables`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("collections", emptyList()).right()
        val useCase = SearchResourcesUseCase(gateway)

        useCase.execute("store-1", SearchResourceType.COLLECTION, "   ", SearchStatusFilter.ALL)

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
    }

    @Test
    fun `execute should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            val cursor = call.variables.jsonObject["cursor"]
            if (cursor == JsonNull) {
                page("articles", listOf(edge(id = "gid://1")), hasNextPage = true, endCursor = "cursor-1").right()
            } else {
                page("articles", listOf(edge(id = "gid://2")), hasNextPage = false).right()
            }
        }
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.ARTICLE, null, SearchStatusFilter.ALL).shouldBeRight()

        gateway.calls.size shouldBe 2
        gateway.calls[1].variables.jsonObject["cursor"]?.jsonPrimitive?.content shouldBe "cursor-1"
        result.resources.map { it.id } shouldBe listOf("gid://1", "gid://2")
        result.truncated shouldBe false
    }

    @Test
    fun `execute should stop at MAX_SEARCH_PAGES and mark the result truncated when the resource never runs out of pages`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = {
            pageCount += 1
            page("collections", listOf(edge(id = "gid://$pageCount")), hasNextPage = true, endCursor = "cursor-$pageCount").right()
        }
        val useCase = SearchResourcesUseCase(gateway)

        val result = useCase.execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.ALL).shouldBeRight()

        gateway.calls.size shouldBe 10
        result.truncated shouldBe true
        result.resources.size shouldBe 10
    }
}
