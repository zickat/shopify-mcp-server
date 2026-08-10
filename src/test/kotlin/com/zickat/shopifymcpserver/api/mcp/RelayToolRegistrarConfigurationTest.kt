package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.collections.shouldBeEmpty
import io.modelcontextprotocol.server.McpServerFeatures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RelayToolRegistrarConfigurationTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var nativeToolNames: NativeToolNames

    @Autowired
    private lateinit var relayToolSpecifications: List<McpServerFeatures.SyncToolSpecification>

    @Test
    fun `the registered relay tool specifications carry none of the real native tool names`() {
        val relayedNames = relayToolSpecifications.map { it.tool().name() }.toSet()

        (relayedNames intersect nativeToolNames.names).shouldBeEmpty()
    }
}
