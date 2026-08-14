package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class NativeToolKinds(context: ApplicationContext) {

    val kinds: Map<String, UseCaseKind> = context.beanDefinitionNames
        .mapNotNull { name -> context.getType(name)?.let { name to it } }
        .flatMap { (beanName, type) ->
            type.methods.filter { it.isAnnotationPresent(McpTool::class.java) }
                .map { it.getAnnotation(McpTool::class.java).name to beanName }
        }
        .mapNotNull { (toolName, beanName) ->
            (context.getBean(beanName) as? HasToolUseCase)?.let { toolName to it.toolUseCase.kind }
        }
        .toMap()
}
