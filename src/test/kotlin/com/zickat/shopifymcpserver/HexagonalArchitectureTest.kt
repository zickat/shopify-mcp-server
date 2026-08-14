package com.zickat.shopifymcpserver

import arrow.core.Either
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.annotation.McpTool
import java.io.File

internal const val BASE_PACKAGE = "com.zickat.shopifymcpserver"

internal fun JavaClass.moduleName(): String? {
    if (!packageName.startsWith("$BASE_PACKAGE.")) return null
    return packageName.removePrefix("$BASE_PACKAGE.").substringBefore('.')
}

internal fun JavaClass.layerName(): String? {
    if (!packageName.startsWith("$BASE_PACKAGE.")) return null
    val afterModule = packageName.removePrefix("$BASE_PACKAGE.").substringAfter('.', "")
    return afterModule.substringBefore('.').takeIf { it.isNotEmpty() }
}

class HexagonalArchitectureTest {

    private val classes: JavaClasses = importedClasses

    @Test
    fun domainHasNoFrameworkDependency() {
        r1ViolatingClasses().unexemptedOnly().fullNames().shouldBeEmpty()
    }

    @Test
    fun domainHasNoWireFormatDependency() {
        r2ViolatingClasses().unexemptedOnly().fullNames().shouldBeEmpty()
    }

    @Test
    fun spiDoesNotDependOnApi() {
        r3ViolatingClasses().fullNames().shouldBeEmpty()
    }

    @Test
    fun apiDoesNotDependOnSpi() {
        r4ViolatingClasses().fullNames().shouldBeEmpty()
    }

    @Test
    fun domainDependsOnNeitherSpiNorApi() {
        r5ViolatingClasses().fullNames().shouldBeEmpty()
    }

    @Test
    fun crossModuleAccessOnlyThroughExposedInterface() {
        r6ViolatingClasses().fullNames().shouldBeEmpty()
    }

    @Test
    fun aclTypesDoNotReferenceTheirOwnDomain() {
        r6bViolatingClasses().unexemptedOnly().fullNames().shouldBeEmpty()
    }

    @Test
    fun catalogDomainAndSpiStayInsideTheirFamily() {
        r7ViolatingClasses().fullNames().shouldBeEmpty()
    }

    @Test
    fun exposedServiceImplLivesInExposedInterface() {
        r8ViolatingClasses().unexemptedOnly().fullNames().shouldBeEmpty()
    }

    @Test
    fun mcpToolLivesInApiMcp() {
        r9ViolatingMethods().map { it.methodLabel() }.shouldBeEmpty()
    }

    @Test
    fun exposedResultsCarryNoRenderedText() {
        r10ViolatingClasses().unexemptedOnly().fullNames().shouldBeEmpty()
    }

    @Test
    fun everyModuleHasAModuleMd() {
        r11ViolatingModules()
            .filterNot { it in NOT_YET_REALIGNED }
            .shouldBeEmpty()
    }

    @Test
    fun useCasePublicMethodsReturnEither() {
        r12ViolatingMethods().map { it.methodLabel() }.shouldBeEmpty()
    }

    @Test
    fun exemptionListContainsNoAlreadyCleanModule() {
        val stillViolatingModules = r1ViolatingClasses().modules() +
            r2ViolatingClasses().modules() +
            r6bViolatingClasses().modules() +
            r8ViolatingClasses().modules() +
            r10ViolatingClasses().modules() +
            r11ViolatingModules()

        (NOT_YET_REALIGNED - stillViolatingModules).shouldBeEmpty()
    }

    private fun r1ViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == DOMAIN_LAYER }
            .filter { it.hasDependencyInAnyPackage(*FRAMEWORK_PACKAGES) }

    private fun r2ViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == DOMAIN_LAYER }
            .filterNot { it.moduleName() == "shopify" }
            .filter { it.hasDependencyInAnyPackage(WIRE_FORMAT_PACKAGE) }

    private fun r3ViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == SPI_LAYER }
            .filter { it.dependsOnLayer(API_LAYER) }

    private fun r4ViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == API_LAYER }
            .filter { it.dependsOnLayer(SPI_LAYER) }

    private fun r5ViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == DOMAIN_LAYER }
            .filter { it.dependsOnLayer(SPI_LAYER) || it.dependsOnLayer(API_LAYER) }

    private fun r6ViolatingClasses(): List<JavaClass> =
        classes.filter { it.moduleName() != null }
            .filter { clazz ->
                val ownModule = clazz.moduleName()
                clazz.directDependenciesFromSelf.any { dependency ->
                    val target = dependency.targetClass
                    val targetModule = target.moduleName()
                    targetModule != null &&
                        targetModule != ownModule &&
                        targetModule != SHARED_KERNEL_MODULE &&
                        target.layerName() != EXPOSED_INTERFACE_LAYER
                }
            }

    private fun r6bViolatingClasses(): List<JavaClass> =
        classes.filter { it.layerName() == EXPOSED_INTERFACE_LAYER }
            .filterNot { it.implementsAnExposedInterfaceOfOwnModule() }
            .filter { clazz ->
                val ownModule = clazz.moduleName()
                clazz.directDependenciesFromSelf.any { dependency ->
                    val target = dependency.targetClass
                    target.moduleName() == ownModule && target.layerName() == DOMAIN_LAYER
                }
            }

    private fun r7ViolatingClasses(): List<JavaClass> =
        classes.filter { clazz ->
            val module = clazz.moduleName()
            module != null &&
                (module in CATALOG_MODULES || module in CROSS_CUTTING_VIEW_MODULES) &&
                clazz.layerName() in setOf(DOMAIN_LAYER, SPI_LAYER)
        }.filter { clazz ->
            val ownModule = requireNotNull(clazz.moduleName())
            val allowedModules = if (ownModule in CROSS_CUTTING_VIEW_MODULES) {
                CATALOG_FAMILY_ALLOWED + CATALOG_MODULES
            } else {
                CATALOG_FAMILY_ALLOWED
            } + ownModule
            clazz.directDependenciesFromSelf.any { dependency ->
                val targetModule = dependency.targetClass.moduleName()
                targetModule != null && targetModule !in allowedModules
            }
        }

    private fun r8ViolatingClasses(): List<JavaClass> =
        classes.filter { it.implementsAnExposedInterfaceOfOwnModule() }
            .filterNot { it.layerName() == EXPOSED_INTERFACE_LAYER }

    private fun r9ViolatingMethods(): List<JavaMethod> =
        classes.flatMap { it.methods }
            .filter { it.isAnnotatedWith(McpTool::class.java) }
            .filterNot { it.owner.packageName.endsWith(".api.mcp") }

    private fun r10ViolatingClasses(): List<JavaClass> =
        classes.filter { it.simpleName.endsWith("Result") }
            .filter { resideInAPackage(EXPOSED_INTERFACE_MODEL_PATTERN).test(it) || it.layerName() == DOMAIN_LAYER }
            .filter { clazz -> clazz.fields.any { field -> field.name == "text" && field.rawType.name == "java.lang.String" } }

    private fun r11ViolatingModules(): Set<String> {
        val knownModules = classes.mapNotNull { it.moduleName() }.toSet()
        return knownModules.filterNot { moduleMdExists(it) }.toSet()
    }

    private fun r12ViolatingMethods(): List<JavaMethod> =
        classes.filter { it.layerName() == DOMAIN_LAYER }
            .filterNot { it.isInterface }
            .filter { it.simpleName.endsWith("UseCase") }
            .flatMap { it.methods }
            .filter { it.modifiers.contains(JavaModifier.PUBLIC) }
            .filterNot { it.modifiers.contains(JavaModifier.SYNTHETIC) }
            .filterNot { it.rawReturnType.isEquivalentTo(Either::class.java) }

    private fun moduleMdExists(module: String): Boolean =
        File("src/main/kotlin/$BASE_PACKAGE_PATH/$module/module.md").isFile

    private fun JavaClass.hasDependencyInAnyPackage(vararg patterns: String): Boolean =
        directDependenciesFromSelf.any { dependency -> patterns.any { resideInAPackage(it).test(dependency.targetClass) } }

    private fun JavaClass.dependsOnLayer(layer: String): Boolean =
        directDependenciesFromSelf.any { it.targetClass.layerName() == layer }

    private fun JavaClass.implementsAnExposedInterfaceOfOwnModule(): Boolean {
        val ownModule = moduleName() ?: return false
        return rawInterfaces.any { it.moduleName() == ownModule && it.layerName() == EXPOSED_INTERFACE_LAYER }
    }

    private fun List<JavaClass>.unexemptedOnly(): List<JavaClass> = filterNot { it.moduleName() in NOT_YET_REALIGNED }

    private fun List<JavaClass>.modules(): Set<String> = mapNotNull { it.moduleName() }.toSet()

    private fun List<JavaClass>.fullNames(): List<String> = map { it.fullName }

    private fun JavaMethod.methodLabel(): String = "${owner.fullName}#$name"

    companion object {
        private const val BASE_PACKAGE_PATH = "com/zickat/shopifymcpserver"

        private const val DOMAIN_LAYER = "domain"
        private const val SPI_LAYER = "spi"
        private const val API_LAYER = "api"
        private const val EXPOSED_INTERFACE_LAYER = "exposed_interface"
        private const val SHARED_KERNEL_MODULE = "shared_kernel"

        private const val EXPOSED_INTERFACE_MODEL_PATTERN = "..exposed_interface.model.."
        private const val WIRE_FORMAT_PACKAGE = "kotlinx.serialization.json.."

        private val FRAMEWORK_PACKAGES = arrayOf(
            "org.springframework..",
            "jakarta..",
            "com.mongodb..",
            "org.bson..",
            "io.modelcontextprotocol..",
            "org.modelcontextprotocol..",
            "okhttp3..",
            "com.mongock..",
        )

        private val CATALOG_MODULES = setOf("pages", "products", "seo", "menus", "metaobjects", "redirects", "collections")
        private val CROSS_CUTTING_VIEW_MODULES = setOf("catalog_status")
        private val CATALOG_FAMILY_ALLOWED = setOf("shared_kernel", "shopify")

        private val NOT_YET_REALIGNED: Set<String> = setOf(
            "api",
            "audit",
            "catalog_status",
            "collections",
            "identity",
            "menus",
            "metaobjects",
            "products",
            "redirects",
            "relay",
            "seo",
            "shared_kernel",
            "shopify",
            "tenancy",
            "vault",
        )

        private val importedClasses: JavaClasses by lazy {
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE)
        }
    }
}
