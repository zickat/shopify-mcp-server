package com.zickat.shopifymcpserver.relay

import com.zickat.shopifymcpserver.api.mcp.NativeToolNames
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.yaml.snakeyaml.Yaml

@SpringBootTest
class RelayManifestCompletenessTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var nativeToolNames: NativeToolNames

    private data class ManifestRow(val toolName: String, val route: ToolRoute)

    private val manifest: List<ManifestRow> = loadManifestFromApplicationYml()

    private val tsRepoToolsDir = Path.of(System.getProperty("user.home"), "IA_sandbox", "mcp-shopify-catalog", "src", "tools")
    private val tsRepoIndexFile = Path.of(System.getProperty("user.home"), "IA_sandbox", "mcp-shopify-catalog", "src", "index.ts")
    private val tsRepoBuildServerFile = Path.of(System.getProperty("user.home"), "IA_sandbox", "mcp-shopify-catalog", "src", "build-server.ts")

    @Test
    fun `the manifest has no duplicated tool name`() {
        val duplicated = manifest.groupBy { it.toolName }.filterValues { it.size > 1 }.keys
        duplicated.shouldBeEmpty()
    }

    @Test
    fun `every McpTool bean has a manifest entry`() {
        nativeToolNames.names.forEach { toolName ->
            checkNotNull(manifest.firstOrNull { it.toolName == toolName }) {
                "'$toolName' has an @McpTool bean but no manifest entry"
            }
        }
    }

    @Test
    fun `every manifest entry not backed by an McpTool bean is routed RELAIS`() {
        manifest.filterNot { it.toolName in nativeToolNames.names }.forEach { it.route shouldBe ToolRoute.RELAIS }
    }

    @Test
    fun `the manifest names match exactly the real registerTool names of the TS repo — no missing tool, no phantom entry`() {
        assumeTrue(Files.isDirectory(tsRepoToolsDir) && Files.isRegularFile(tsRepoIndexFile) && Files.isRegularFile(tsRepoBuildServerFile)) {
            "mcp-shopify-catalog repo not found next to this one at $tsRepoToolsDir — skipping (this test needs both repos co-located, as they are on Val's machine)"
        }

        val realNames = extractRealToolNames()
        val manifestNames = manifest.map { it.toolName }.toSet()

        val missingFromManifest = realNames - manifestNames
        val phantomInManifest = manifestNames - realNames

        missingFromManifest.shouldBeEmpty()
        phantomInManifest.shouldBeEmpty()
        manifestNames shouldHaveSize realNames.size
    }

    private fun extractRealToolNames(): Set<String> {
        val literalNamePattern = Regex("registerTool\\(\\s*\"([a-zA-Z0-9_]+)\"")
        val registerToolCallPattern = Regex("registerTool\\(")
        val objectLiteralNamePattern = Regex("\\{\\s*name:\\s*\"([a-zA-Z0-9_]+)\"")

        val tsFiles = Files.list(tsRepoToolsDir).use { stream ->
            stream.filter { it.toString().endsWith(".ts") }.toList()
        } + listOf(tsRepoIndexFile, tsRepoBuildServerFile)

        val names = mutableSetOf<String>()
        tsFiles.forEach { file ->
            val content = File(file.toString()).readText()
            val literalMatches = literalNamePattern.findAll(content).map { it.groupValues[1] }.toList()
            names += literalMatches

            val callCount = registerToolCallPattern.findAll(content).count()
            if (callCount > literalMatches.size) {
                names += objectLiteralNamePattern.findAll(content).map { it.groupValues[1] }.toSet()
            }
        }
        return names
    }

    private fun loadManifestFromApplicationYml(): List<ManifestRow> {
        val stream = javaClass.classLoader.getResourceAsStream("application.yml")
            ?: error("application.yml not found on the test classpath")
        val root = Yaml().load<Map<String, Any?>>(stream)
        @Suppress("UNCHECKED_CAST")
        val relay = root["relay"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val entries = relay["manifest"] as List<Map<String, Any?>>
        return entries.map { entry ->
            ManifestRow(
                toolName = entry["tool-name"] as String,
                route = ToolRoute.valueOf(entry["route"] as String),
            )
        }
    }
}
