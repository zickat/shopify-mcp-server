package com.zickat.shopifymcpserver.relay.domain

import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RelayManifestTest {

    @Test
    fun `entryFor returns null for a tool name absent from the manifest — closed by default`() {
        val manifest = RelayManifest(listOf(RelayManifestEntry("known_tool", ToolRoute.RELAIS, UseCaseKind.READ)))

        manifest.entryFor("unknown_tool") shouldBe null
    }

    @Test
    fun `entryFor returns the entry for a tool name present in the manifest`() {
        val entry = RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)
        val manifest = RelayManifest(listOf(entry))

        manifest.entryFor("check_shopify_connection") shouldBe entry
    }

    @Test
    fun `relayedTools only lists entries routed RELAIS, never NATIF entries`() {
        val manifest = RelayManifest(
            listOf(
                RelayManifestEntry("relayed_tool", ToolRoute.RELAIS, UseCaseKind.READ),
                RelayManifestEntry("native_tool", ToolRoute.NATIF, UseCaseKind.MUTATION),
            ),
        )

        manifest.relayedTools().map { it.toolName } shouldBe listOf("relayed_tool")
    }

    @Test
    fun `removing an entry from the manifest makes it unresolvable, exactly as if it had never been listed`() {
        val withEntry = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val withoutEntry = RelayManifest(emptyList())

        (withEntry.entryFor("check_shopify_connection") != null) shouldBe true
        withoutEntry.entryFor("check_shopify_connection") shouldBe null
        withoutEntry.relayedTools() shouldBe emptyList()
    }
}
