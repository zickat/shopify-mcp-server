package com.zickat.shopifymcpserver.api.exposed_interface

import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.zickat.shopifymcpserver.layerName
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExposedInterfaceLayerAnchoringTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.zickat.shopifymcpserver")

    @Test
    fun `RoutedToolPipeline moved out of api_mcp is anchored to the exposed_interface layer, which R6's allow-list permits across modules`() {
        val target = classes.get("com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline")

        target.layerName() shouldBe "exposed_interface"
    }

    @Test
    fun `AuthenticatedToolPipeline moved out of api_mcp is anchored to the exposed_interface layer, which R6's allow-list permits across modules`() {
        val target = classes.get("com.zickat.shopifymcpserver.api.exposed_interface.AuthenticatedToolPipeline")

        target.layerName() shouldBe "exposed_interface"
    }

    @Test
    fun exposedInterfacePackagesAreAnchoredAtModuleRoot() {
        val exposedInterfaceClasses = classes.filter { resideInAPackage("..exposed_interface..").test(it) }

        exposedInterfaceClasses.shouldNotBeEmpty()
        exposedInterfaceClasses.forEach { it.layerName() shouldBe "exposed_interface" }
    }
}
