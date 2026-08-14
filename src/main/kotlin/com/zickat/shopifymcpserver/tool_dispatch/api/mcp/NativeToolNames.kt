package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class NativeToolNames(context: ApplicationContext) {

    val names: Set<String> = context.beanDefinitionNames
        .mapNotNull { context.getType(it) }
        .flatMap { it.methods.toList() }
        .filter { it.isAnnotationPresent(McpTool::class.java) }
        .map { it.getAnnotation(McpTool::class.java).name }
        .toSet()
}
