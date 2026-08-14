package com.zickat.shopifymcpserver.catalog_status.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.catalog_status.CatalogStatusFakeRepository
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusListing
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusResourceNode
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SearchResourcesUseCaseTest {

    private fun useCase(repository: CatalogStatusFakeRepository = CatalogStatusFakeRepository()) = SearchResourcesUseCase(repository)

    private fun node(
        id: String = "gid://1",
        title: String = "Title",
        handle: String = "handle",
        contentStatus: String? = null,
        summary: String? = null,
        secondarySignal: String? = null,
    ) = CatalogStatusResourceNode(id, title, handle, contentStatus, summary, secondarySignal)

    private fun repositoryReturning(vararg nodes: CatalogStatusResourceNode, truncated: Boolean = false) =
        CatalogStatusFakeRepository().apply { searchResponse = CatalogStatusListing(nodes.toList(), truncated).right() }

    @Test
    fun `execute should not treat a collection as untreated when content_status is absent but summary is non-empty`() {
        val repository = repositoryReturning(node(summary = "Already enriched"))

        val result = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.shouldBeEmpty()
    }

    @Test
    fun `execute should not treat a collection as untreated when content_status is absent but intro_text is non-empty rich text`() {
        val introText = """{"type":"root","children":[{"type":"paragraph","children":[{"type":"text","value":"Some intro"}]}]}"""
        val repository = repositoryReturning(node(secondarySignal = introText))

        val result = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.shouldBeEmpty()
    }

    @Test
    fun `execute should treat a collection as untreated when intro_text is a JSON-non-null but textually empty Lexical document`() {
        val emptyLexical = """{"type":"root","children":[]}"""
        val repository = repositoryReturning(node(id = "gid://1", secondarySignal = emptyLexical))

        val result = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.map { it.id } shouldBe listOf("gid://1")
    }

    @Test
    fun `execute should not treat an article as untreated when content_status is absent but sections is a non-empty list`() {
        val repository = repositoryReturning(node(secondarySignal = """["one section"]"""))

        val result = useCase(repository).execute("store-1", SearchResourceType.ARTICLE, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.shouldBeEmpty()
    }

    @Test
    fun `execute should treat an article as untreated when sections is a JSON-non-null but empty array`() {
        val repository = repositoryReturning(node(id = "gid://1", secondarySignal = "[]"))

        val result = useCase(repository).execute("store-1", SearchResourceType.ARTICLE, null, SearchStatusFilter.UNTREATED).shouldBeRight()

        result.resources.map { it.id } shouldBe listOf("gid://1")
    }

    @Test
    fun `execute should filter to_review and blocked strictly on content_status`() {
        val repository = repositoryReturning(
            node(id = "gid://to-review", contentStatus = "to_review"),
            node(id = "gid://blocked", contentStatus = "blocked"),
            node(id = "gid://untreated"),
        )

        val toReview = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.TO_REVIEW).shouldBeRight()
        val blocked = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.BLOCKED).shouldBeRight()

        toReview.resources.map { it.id } shouldBe listOf("gid://to-review")
        blocked.resources.map { it.id } shouldBe listOf("gid://blocked")
    }

    @Test
    fun `execute with all should return every resource regardless of status and label untreated ones accordingly`() {
        val repository = repositoryReturning(
            node(id = "gid://to-review", contentStatus = "to_review"),
            node(id = "gid://untreated"),
        )

        val result = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.ALL).shouldBeRight()

        result.resources.map { it.id to it.contentStatus } shouldBe listOf(
            "gid://to-review" to "to_review",
            "gid://untreated" to "untreated",
        )
    }

    @Test
    fun `execute should trim a blank query down to null before calling the repository`() {
        val repository = repositoryReturning()

        useCase(repository).execute("store-1", SearchResourceType.COLLECTION, "   ", SearchStatusFilter.ALL)

        repository.searchCalls.single().query shouldBe null
    }

    @Test
    fun `execute should pass a non-blank query untouched to the repository`() {
        val repository = repositoryReturning()

        useCase(repository).execute("store-1", SearchResourceType.COLLECTION, "title:*word*", SearchStatusFilter.ALL)

        repository.searchCalls.single().query shouldBe "title:*word*"
    }

    @Test
    fun `execute should pass the truncated flag from the repository through untouched`() {
        val repository = repositoryReturning(node(id = "gid://1"), truncated = true)

        val result = useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.ALL).shouldBeRight()

        result.truncated shouldBe true
    }

    @Test
    fun `execute should propagate a Left when the repository fails technically`() {
        val repository = CatalogStatusFakeRepository().apply { searchResponse = TechnicalError("shopify.graphql.response.malformed").left() }

        useCase(repository).execute("store-1", SearchResourceType.COLLECTION, null, SearchStatusFilter.ALL).shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }
}
