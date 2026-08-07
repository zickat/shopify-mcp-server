package com.zickat.shopifymcpserver.relay.domain

import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.domain.models.ToolRoute

class RelayManifest(entries: List<RelayManifestEntry>) {

    private val byToolName: Map<String, RelayManifestEntry> = entries.associateBy { it.toolName }

    fun entryFor(toolName: String): RelayManifestEntry? = byToolName[toolName]

    fun relayedTools(): List<RelayManifestEntry> = byToolName.values.filter { it.route == ToolRoute.RELAIS }
}
