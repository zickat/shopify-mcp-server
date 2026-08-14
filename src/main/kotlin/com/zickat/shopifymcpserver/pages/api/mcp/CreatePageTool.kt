package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.pages.domain.CreatePageUseCase
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class CreatePageTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val createPageUseCase: CreatePageUseCase,
) : HasToolUseCase {

    private object CreatePageToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = CreatePageToolKind

    @McpTool(
        name = "create_page",
        description = "Creates a Shopify Page (title + native HTML body, optional handle). DRAFT by " +
            "default (isPublished: false, always passed explicitly) — on this store a Page created " +
            "without an explicit status is published by default, so the draft is guaranteed by the " +
            "code: use publish_page after review to go live. publish: true creates it published " +
            "directly (explicit request). body is HTML stored AS-IS and rendered on the storefront " +
            "(no sanitizer client-side — the native admin accepts the same HTML): draft-by-default is " +
            "the defense in depth. handle is never invented client-side: omitted -> Shopify generates " +
            "it from the title and the response returns the handle actually assigned; supplied -> " +
            "used as-is (a conflict surfaces via userErrors). Never writes a custom.* metafield (see " +
            "update_page_metafields).",
    )
    fun createPage(
        @McpToolParam(description = "Page title, e.g. \"Shipping and returns\"", required = true)
        title: String,
        @McpToolParam(description = "Native HTML body of the Page — stored as-is, rendered on the storefront (no client-side sanitizing).", required = true)
        body: String,
        @McpToolParam(
            description = "OPTIONAL — URL handle. Omitted = Shopify generates it from the title (the " +
                "response returns the real handle). Never slugified/invented client-side.",
            required = false,
        )
        handle: String?,
        @McpToolParam(
            description = "OPTIONAL, defaults to false (draft). true = immediate explicit publication. " +
                "The draft default is guaranteed by the code, never left to Shopify's implicit default.",
            required = false,
        )
        publish: Boolean?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("title", title)
            put("body", body)
            handle?.let { put("handle", it) }
            publish?.let { put("publish", it.toString()) }
        }

        return pipeline.runForActiveStore(
            "create_page",
            CreatePageToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            createPageUseCase.execute(tenant.storeId, title, body, handle, publish).fold(
                { error -> PageToolResults.errorResult(slug, error) },
                { result -> PageToolResults.createPageResult(slug, result) },
            )
        }
    }
}
