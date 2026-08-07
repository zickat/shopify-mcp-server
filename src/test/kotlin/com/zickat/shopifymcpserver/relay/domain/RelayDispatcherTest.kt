package com.zickat.shopifymcpserver.relay.domain

import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.domain.models.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class RelayDispatcherTest {

    private val emptyInput = JsonObject(emptyMap())

    @Test
    fun `a tool absent from the manifest is refused and the TS process is never called — closed by default`() {
        val manifest = RelayManifest(emptyList())
        val tsClient = RelayTsClientFake()
        val dispatcher = RelayDispatcher(manifest, tsClient)

        val error = dispatcher.callRelayedTool("unknown_tool", emptyInput, "store-1", AccessRole.OPERATOR).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "relay.tool.unmanifested"
        tsClient.callCount.get() shouldBe 0
    }

    @Test
    fun `a tool listed NATIF in the manifest is refused by the relay dispatcher — a route is authoritative on one side only`() {
        val manifest = RelayManifest(listOf(RelayManifestEntry("native_tool", ToolRoute.NATIF, UseCaseKind.READ)))
        val tsClient = RelayTsClientFake()
        val dispatcher = RelayDispatcher(manifest, tsClient)

        val error = dispatcher.callRelayedTool("native_tool", emptyInput, "store-1", AccessRole.OPERATOR).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "relay.tool.unmanifested"
        tsClient.callCount.get() shouldBe 0
    }

    @Test
    fun `a tool removed from the manifest after having been servable is refused, never silently allowed through`() {
        val entry = RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)
        val tsClient = RelayTsClientFake()
        val servable = RelayDispatcher(RelayManifest(listOf(entry)), tsClient)
        val afterRemoval = RelayDispatcher(RelayManifest(emptyList()), tsClient)

        servable.callRelayedTool("check_shopify_connection", emptyInput, "store-1", AccessRole.OPERATOR).shouldBeRight()
        val error = afterRemoval.callRelayedTool("check_shopify_connection", emptyInput, "store-1", AccessRole.OPERATOR).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "relay.tool.unmanifested"
    }

    @Test
    fun `a relayed tool call forwards the exact toolName, storeId and role to the TS client`() {
        val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val tsClient = RelayTsClientFake()
        val dispatcher = RelayDispatcher(manifest, tsClient)

        dispatcher.callRelayedTool("check_shopify_connection", emptyInput, "store-42", AccessRole.VIEWER).shouldBeRight()

        val forwarded = tsClient.calls.single()
        forwarded.toolName shouldBe "check_shopify_connection"
        forwarded.storeId shouldBe "store-42"
        forwarded.role shouldBe "VIEWER"
    }

    @Test
    fun `eight concurrent calls through the dispatcher never overlap in flight toward the TS process — the serialization guard`() {
        val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
        val tsClient = RelayTsClientFake(delayMillis = 50)
        val dispatcher = RelayDispatcher(manifest, tsClient)
        val threadCount = 8
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        repeat(threadCount) { index ->
            Thread {
                readyLatch.countDown()
                startLatch.await()
                dispatcher.callRelayedTool("check_shopify_connection", emptyInput, "store-$index", AccessRole.OPERATOR)
                doneLatch.countDown()
            }.start()
        }
        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)

        tsClient.callCount.get() shouldBe threadCount
        tsClient.maxConcurrentInvocations.get() shouldBe 1
    }
}
