package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.relay.RelayProperties
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

private class ListStoresFakeBean {
    @McpTool(name = "list_stores", description = "fake")
    fun call(): String = "ok"
}

private class UseStoreFakeBean {
    @McpTool(name = "use_store", description = "fake")
    fun call(): String = "ok"
}

private class CreateRedirectFakeBean {
    @McpTool(name = "create_redirect", description = "fake")
    fun call(): String = "ok"
}

private class SearchResourcesFakeBean {
    @McpTool(name = "search_resources", description = "fake")
    fun call(): String = "ok"
}

private class ListMenusFakeBean {
    @McpTool(name = "list_menus", description = "fake")
    fun call(): String = "ok"
}

private class GetSeoFakeBean {
    @McpTool(name = "get_seo", description = "fake")
    fun call(): String = "ok"
}

private class ListMetaobjectsFakeBean {
    @McpTool(name = "list_metaobjects", description = "fake")
    fun call(): String = "ok"
}

private class GetMetaobjectFakeBean {
    @McpTool(name = "get_metaobject", description = "fake")
    fun call(): String = "ok"
}

private class ListPagesFakeBean {
    @McpTool(name = "list_pages", description = "fake")
    fun call(): String = "ok"
}

private class GetPageMetafieldsFakeBean {
    @McpTool(name = "get_page_metafields", description = "fake")
    fun call(): String = "ok"
}

private class SearchProductsFakeBean {
    @McpTool(name = "search_products", description = "fake")
    fun call(): String = "ok"
}

private class GetRawContentFakeBean {
    @McpTool(name = "get_raw_content", description = "fake")
    fun call(): String = "ok"
}

private class GetEnrichedContentFakeBean {
    @McpTool(name = "get_enriched_content", description = "fake")
    fun call(): String = "ok"
}

private class ListToReviewFakeBean {
    @McpTool(name = "list_to_review", description = "fake")
    fun call(): String = "ok"
}

private class ListOrphanProductsFakeBean {
    @McpTool(name = "list_orphan_products", description = "fake")
    fun call(): String = "ok"
}

class RelayManifestInvariantRunnerTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(NativeToolNames::class.java)
        .withBean(RelayManifestInvariantRunner::class.java)

    private fun manifestOf(vararg entries: Pair<String, ToolRoute>) =
        RelayProperties(manifest = entries.map { (name, route) -> RelayProperties.ManifestEntry(name, route, UseCaseKind.READ) })

    @Test
    fun `should refuse to start when a manifest NATIF entry has no matching bean`() {
        contextRunner
            .withBean(RelayProperties::class.java, Supplier { manifestOf("ghost_tool" to ToolRoute.NATIF) })
            .run { context ->
                val runner = context.getBean(RelayManifestInvariantRunner::class.java)
                val failure = shouldThrow<IllegalStateException> { runner.run(DefaultApplicationArguments()) }
                failure.message shouldContain "ghost_tool"
            }
    }

    @Test
    fun `should start normally when the real manifest NATIF entries cover exactly the real native beans`() {
        contextRunner
            .withBean(ListStoresFakeBean::class.java)
            .withBean(UseStoreFakeBean::class.java)
            .withBean(CreateRedirectFakeBean::class.java)
            .withBean(SearchResourcesFakeBean::class.java)
            .withBean(ListMenusFakeBean::class.java)
            .withBean(GetSeoFakeBean::class.java)
            .withBean(ListMetaobjectsFakeBean::class.java)
            .withBean(GetMetaobjectFakeBean::class.java)
            .withBean(ListPagesFakeBean::class.java)
            .withBean(GetPageMetafieldsFakeBean::class.java)
            .withBean(SearchProductsFakeBean::class.java)
            .withBean(GetRawContentFakeBean::class.java)
            .withBean(GetEnrichedContentFakeBean::class.java)
            .withBean(ListToReviewFakeBean::class.java)
            .withBean(ListOrphanProductsFakeBean::class.java)
            .withBean(
                RelayProperties::class.java,
                Supplier {
                    manifestOf(
                        "list_stores" to ToolRoute.NATIF,
                        "use_store" to ToolRoute.NATIF,
                        "create_redirect" to ToolRoute.NATIF,
                        "search_resources" to ToolRoute.NATIF,
                        "list_menus" to ToolRoute.NATIF,
                        "get_seo" to ToolRoute.NATIF,
                        "list_metaobjects" to ToolRoute.NATIF,
                        "get_metaobject" to ToolRoute.NATIF,
                        "list_pages" to ToolRoute.NATIF,
                        "get_page_metafields" to ToolRoute.NATIF,
                        "search_products" to ToolRoute.NATIF,
                        "get_raw_content" to ToolRoute.NATIF,
                        "get_enriched_content" to ToolRoute.NATIF,
                        "list_to_review" to ToolRoute.NATIF,
                        "list_orphan_products" to ToolRoute.NATIF,
                    )
                },
            )
            .run { context ->
                context.getBean(RelayManifestInvariantRunner::class.java).run(DefaultApplicationArguments())
            }
    }

    @Test
    fun `should refuse to start when a native bean has no manifest entry at all`() {
        contextRunner
            .withBean(ListStoresFakeBean::class.java)
            .withBean(RelayProperties::class.java, Supplier { manifestOf() })
            .run { context ->
                val runner = context.getBean(RelayManifestInvariantRunner::class.java)
                val failure = shouldThrow<IllegalStateException> { runner.run(DefaultApplicationArguments()) }
                failure.message shouldContain "list_stores"
            }
    }

    @Test
    fun `should start normally when a native bean's manifest entry was switched back to RELAIS`() {
        contextRunner
            .withBean(ListStoresFakeBean::class.java)
            .withBean(RelayProperties::class.java, Supplier { manifestOf("list_stores" to ToolRoute.RELAIS) })
            .run { context ->
                context.getBean(RelayManifestInvariantRunner::class.java).run(DefaultApplicationArguments())
            }
    }
}
