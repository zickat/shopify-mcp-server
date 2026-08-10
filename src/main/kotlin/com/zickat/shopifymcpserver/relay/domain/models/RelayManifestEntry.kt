package com.zickat.shopifymcpserver.relay.domain.models

import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind

data class RelayManifestEntry(
    val toolName: String,
    val route: ToolRoute,
    val kind: UseCaseKind,
)
