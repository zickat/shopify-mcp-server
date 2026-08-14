package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method

private data class NativeToolContract(val name: String, val description: String, val inputSchema: JsonElement)

private val canonicalJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun JsonElement.sortedRecursively(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.sortedRecursively() })
    is JsonArray -> JsonArray(map { it.sortedRecursively() })
    else -> this
}

@SpringBootTest
class NativeToolContractsTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var context: ApplicationContext

    private fun discoverNativeToolMethods(): List<Method> =
        context.beanDefinitionNames
            .mapNotNull { context.getType(it) }
            .flatMap { it.methods.toList() }
            .filter { it.isAnnotationPresent(McpTool::class.java) }

    private fun contractOf(method: Method): NativeToolContract {
        val annotation = method.getAnnotation(McpTool::class.java)
        val name = annotation.name.ifBlank { method.name }
        val inputSchema = canonicalJson.parseToJsonElement(McpJsonSchemaGenerator.generateForMethodInput(method))
        return NativeToolContract(name, annotation.description, inputSchema)
    }

    private fun currentContractsByName(): Map<String, NativeToolContract> =
        discoverNativeToolMethods().map { contractOf(it) }.associateBy { it.name }

    private fun referenceContractsByName(): Map<String, NativeToolContract> {
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream("native-tool-contracts.json")) {
            "no native-tool-contracts.json resource found on the test classpath"
        }
        val text = resource.bufferedReader().use { it.readText() }
        val array = canonicalJson.parseToJsonElement(text) as JsonArray
        return array.associate { entry ->
            val tool = entry as JsonObject
            val name = tool.getValue("name").jsonPrimitive.content
            name to NativeToolContract(name, tool.getValue("description").jsonPrimitive.content, tool.getValue("inputSchema"))
        }
    }

    @Test
    fun `discovers exactly the twenty-eight native tool methods carrying McpTool`() {
        discoverNativeToolMethods().map { it.getAnnotation(McpTool::class.java).name.ifBlank { it.name } }
            .toSet() shouldHaveSize 28
    }

    @Test
    fun `every native tool contract matches the versioned snapshot — name, description, inputSchema`() {
        val current = currentContractsByName()
        val reference = referenceContractsByName()

        val newToolsMissingFromSnapshot = (current.keys - reference.keys)
            .map { "'$it' is a native tool in the running context but has no entry in native-tool-contracts.json" }
        val snapshotToolsNoLongerNative = (reference.keys - current.keys)
            .map { "'$it' has a native-tool-contracts.json entry but no matching @McpTool method in the running context" }
        val driftedFields = (current.keys intersect reference.keys).flatMap { name ->
            val cur = current.getValue(name)
            val ref = reference.getValue(name)
            buildList {
                if (cur.description != ref.description) add("'$name': description differs from native-tool-contracts.json")
                if (cur.inputSchema.sortedRecursively() != ref.inputSchema.sortedRecursively()) {
                    add("'$name': inputSchema differs from native-tool-contracts.json")
                }
            }
        }

        val problems = newToolsMissingFromSnapshot + snapshotToolsNoLongerNative + driftedFields

        problems shouldBe emptyList()
    }
}
