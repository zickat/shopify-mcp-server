package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.pages.domain.ListPagesUseCase
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class ListPagesTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val listPagesUseCase: ListPagesUseCase,
) : HasToolUseCase {

    private object ListPagesToolKind : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListPagesToolKind

    @McpTool(
        name = "list_pages",
        description = "Lists the Pages of the store (id, title, handle). Without query: every Page. " +
            "With query (Shopify Admin search syntax, e.g. title:*guide*, handle:guide-hub): filters " +
            "the result. Read-only — discovery tool ahead of get_page_metafields/update_page_metafields, " +
            "which both require an already-known page_id.",
    )
    fun listPages(
        @McpToolParam(
            description = "OPTIONAL — Shopify Admin search query (e.g. \"title:*guide*\", " +
                "\"handle:guide-hub\"). Omitted or empty = every Page of the store.",
            required = false,
        )
        query: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap { query?.let { put("query", it) } }

        return pipeline.runForActiveStore(
            "list_pages",
            ListPagesToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            listPagesUseCase.execute(tenant.storeId, query).fold(
                { error -> PageToolResults.errorResult(slug, error) },
                { result -> PageToolResults.listPagesResult(slug, result) },
            )
        }
    }
}
