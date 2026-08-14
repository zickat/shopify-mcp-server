package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.relay.domain.RelayDispatcher
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGatewayImpl
import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.RelayTsClientFake
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
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

class RelayManifestInvariantRunnerTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(NativeToolNames::class.java)
        .withBean(RelayManifestInvariantRunner::class.java)

    private fun gatewayOf(vararg entries: Pair<String, ToolRoute>): RelayGateway {
        val manifest = RelayManifest(entries.map { (name, route) -> RelayManifestEntry(name, route, UseCaseKind.READ) })
        return RelayGatewayImpl(manifest, RelayDispatcher(manifest, RelayTsClientFake()))
    }

    @Test
    fun `should refuse to start when a manifest NATIF entry has no matching bean`() {
        contextRunner
            .withBean(RelayGateway::class.java, Supplier { gatewayOf("ghost_tool" to ToolRoute.NATIF) })
            .run { context ->
                val runner = context.getBean(RelayManifestInvariantRunner::class.java)
                val failure = shouldThrow<IllegalStateException> { runner.run(DefaultApplicationArguments()) }
                failure.message shouldContain "ghost_tool"
            }
    }

    @Test
    fun `should refuse to start when a native bean has no manifest entry at all`() {
        contextRunner
            .withBean(ListStoresFakeBean::class.java)
            .withBean(RelayGateway::class.java, Supplier { gatewayOf() })
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
            .withBean(RelayGateway::class.java, Supplier { gatewayOf("list_stores" to ToolRoute.RELAIS) })
            .run { context ->
                context.getBean(RelayManifestInvariantRunner::class.java).run(DefaultApplicationArguments())
            }
    }
}
