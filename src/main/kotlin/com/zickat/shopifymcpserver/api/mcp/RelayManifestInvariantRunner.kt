package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.relay.RelayProperties
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RelayManifestInvariantRunner(
    private val nativeToolNames: NativeToolNames,
    private val relayProperties: RelayProperties,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val manifestToolNames = relayProperties.manifest.map { it.toolName }.toSet()
        val manifestNatifNames = relayProperties.manifest
            .filter { it.route == ToolRoute.NATIF }
            .map { it.toolName }
            .toSet()

        val declaredWithoutBean = manifestNatifNames - nativeToolNames.names
        val beanWithoutEntry = nativeToolNames.names - manifestToolNames

        check(declaredWithoutBean.isEmpty() && beanWithoutEntry.isEmpty()) {
            "relay manifest / native beans mismatch — " +
                "declared NATIF without a bean: $declaredWithoutBean, " +
                "native bean without any manifest entry: $beanWithoutEntry"
        }
    }
}
