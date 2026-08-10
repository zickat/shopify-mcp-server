package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.pages.exposed_interface.PagesExposedService
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.isGidOfType
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class GetPageMetafieldsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val pagesExposedService: PagesExposedService,
) {

    private object GetPageMetafieldsToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    @McpTool(
        name = "get_page_metafields",
        description = "Returns the custom.* metafields already written on a Page (guide hub or other " +
            "content page). Without keys: every custom.* metafield found. With keys: filters to the " +
            "requested keys and explicitly reports each one that is not defined (\"non défini\"), " +
            "never a silent omission. Read-only.",
    )
    fun getPageMetafields(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        @McpToolParam(
            description = "OPTIONAL — subset of custom.* keys to return (e.g. [\"themes\"]). Omitted = " +
                "every key found. A requested key absent from the Page is reported \"non défini\", " +
                "never silently omitted.",
            required = false,
        )
        keys: List<String>?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("page_id", page_id)
            keys?.let { put("keys", it.joinToString(",")) }
        }

        return pipeline.runForActiveStore(
            "get_page_metafields",
            GetPageMetafieldsToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!page_id.isGidOfType(PAGE_GID_TYPE)) {
                McpToolResults.invalidGidType(slug, "page_id", page_id, PAGE_GID_TYPE)
            } else {
                pagesExposedService.getPageMetafields(tenant.storeId, page_id, keys).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.getPageMetafieldsResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val PAGE_GID_TYPE = "Page"
    }
}
