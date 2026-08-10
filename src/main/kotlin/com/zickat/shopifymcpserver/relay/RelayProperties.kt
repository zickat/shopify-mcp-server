package com.zickat.shopifymcpserver.relay

import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "relay")
data class RelayProperties(
    val ts: Ts = Ts(),
    val manifest: List<ManifestEntry> = emptyList(),
) {
    data class Ts(val baseUrl: String = "http://127.0.0.1:8765")

    data class ManifestEntry(
        val toolName: String,
        val route: ToolRoute,
        val kind: UseCaseKind,
    )
}
