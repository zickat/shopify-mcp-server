package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RelayManifestInvariantRunner(
    private val nativeToolNames: NativeToolNames,
    private val relayGateway: RelayGateway,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val manifestToolNames = relayGateway.declaredToolNames()
        val manifestNatifNames = relayGateway.declaredNativeToolNames()

        val declaredWithoutBean = manifestNatifNames - nativeToolNames.names
        val beanWithoutEntry = nativeToolNames.names - manifestToolNames

        check(declaredWithoutBean.isEmpty() && beanWithoutEntry.isEmpty()) {
            "relay manifest / native beans mismatch — " +
                "declared NATIF without a bean: $declaredWithoutBean, " +
                "native bean without any manifest entry: $beanWithoutEntry"
        }
    }
}
